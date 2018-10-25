/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * RRPClient.java
 *
 * Created on June 15, 2005, 12:18 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package gov.usgs.anss.net;
//import gov.usgs.anss.edgemom.*;
import gov.usgs.anss.edgeoutput.RingFileInputer;
import gov.usgs.anss.edgeoutput.ChannelHolder;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;

/** RRPClient - is an EdgeThread which Opens a RingFileInputer
 * to a RRPServer.  Optionally the data can be delayed.
 *
 * <PRE>
 * switch  data   Description
 * -ring  filename The filename of the ring to be forwarded via the RRP protocol
 * -ip    ip.adr   The IP address of the RRPServer
 * -p     nnnn     The port on the RRPServer which accepts sockets from this RRPClient
 * -delay  ssss    Number of seconds to delay data
 * -ext    ASCII   The extension to add to the filename.last to make the status file for this instance
 * -npanic n       Number of two minute intervals with no data before doing a panic exit.  Useful on intermittent data (def=10)
 * DEBUG SWITCHES:
 * -force          Force errors on the link for debugging
 * -hdrdump        Turn on logging of headers
 * -log            Often this is started with chkJarProcessTAG and set log file name
 * -dbgbin         Turn on debugging of binary data
 * -dbgch seedname A debug channel name
 * -alert nnnn     Send an alarm event if behind by more than this number of packets, reconnect at 10 minutes and every hour thereafter
 * -forceinit      Start this sender with the next packet - do not send the entire missing data.
 * -noevent        If present, disable all events to alarm for this instance.
 *
 * </PRE>
 *
 *
 * @author davidketchum
 */
public final class RRPClient extends EdgeThread {
  // local object attributes
  private String filename;
  private RingFileInputer in;  
  
  // File and buffers for keeping track of last output
  private RawDisk gotlast;            // Our place to record last seqence acked
  private final byte [] lastbuf;
  private final ByteBuffer lastbb;
  private int lastAckSeq;
  private int forceAckSeq=-1;
  
  // Structure related to socket for sending blocks and reading acks 
  private AckReader ackReader;
  private String ipadr;               // Remote end ip
  private int port;                   // Remote end port
  private String bind;                // Local end IP address
  private Socket s;                   // Socket to remote end
  private InputStream sockin;
  private OutputStream sockout;
  // General EdgeMomThread attributes
  private long lastStatus;     // Time last status was received
  private int behindAlertLimit;
  private boolean forceInit;
  private String ext="";
  private final RRPCMonitor monitor; // Watcher for hung conditions on this thread
  private long inbytes;         // number of input bytes processed
  private long outbytes;        // Number of output bytes processed
  private final RFTCIShutdown shutdown;
  private int delayMS;          // if non-zero, amount of time to delay all data
  private int npanic;           // length of time to wait with no output before panicing
  private boolean dbg;
  private boolean noEvent;
  private boolean dbgbin;               // if true, modify MiniSeed to include sequence in each
  private boolean forceErrors;
  private boolean hdrdump;
  String edgeDigit;
  String logname="rrpc";
  int state;
  @Override
  public String toString() {return tag+" RRPC: inb="+inbytes+" outb="+outbytes+" file="+filename;}
  /** return number of bytes processed as input
   *@return Number of bytes processed as input
   */
  public long getInbytes() {return inbytes;}
  /** return number of bytes processed as input
   *@return Number of bytes processed as input
   */
  public long getOutbytes() {return outbytes;}
  
  /** set debug flat
   *@param t What to set debug to!*/
  public void setDebug(boolean t) {dbg=t;} 
  /** creates an new instance of RRPClient - This one gets its arguments from a command line
   * @param argline The argument line to use to start this client
   * @param tg The tag to use for logging
   */
  public RRPClient(String argline, String tg) {
    super(argline,tg);
    //Util.setTestStream("rrpc.log"+EdgeThread.EdgeThreadDigit());
    //edgeDigit = EdgeThread.EdgeThreadDigit();
    prt(tag+"args="+argline);
    String [] args = argline.split("\\s");
    dbg=false;
    dbgbin=false;
    hdrdump=false;
    forceInit=false;
    tag = tg;
    int dummy=0;
    for(int i=0; i<args.length; i++) {
      //prt(i+" arg="+args[i]);
      if(args[i].length() == 0) continue; 
      if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-empty")) dummy=1;         // supprss warningAllow this for totally empty command lines
      else if(args[i].equals("-ring")) {filename=args[i+1];i++;}
      else if(args[i].equals("-dbgch")) {prta("CH: dbg="+args[i+1]);ChannelHolder.setDebugSeedname(args[i+1]); i++;}
      else if(args[i].equals("-dbgbin")) dbgbin=true;
      else if(args[i].equals("-ip")) {ipadr=args[i+1]; i++;}
      else if(args[i].equals("-p")) { port = Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-forceseq")) { forceAckSeq = Integer.parseInt(args[i+1]); i++;prta(" ******* Force seq set to "+forceAckSeq);}
      else if(args[i].equals("-force")) forceErrors=true;
      else if(args[i].equals("-hdrdump")) hdrdump=true;
      else if(args[i].equals("-forceinit")) forceInit=true;
      else if(args[i].equals("-noevent")) noEvent=true;
      else if(args[i].equals("-ext")) {ext=args[i+1]; i++;}
      else if(args[i].equals("-bind")) {bind=args[i+1]; i++;}
      else if(args[i].equals("-log")) {logname=args[i+1];i++;}
      else if(args[i].equals("-delay")) {delayMS = Integer.parseInt(args[i+1])*1000; i++;}
      else if(args[i].equals("-alert")) {behindAlertLimit=Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-npanic")) {npanic=Integer.parseInt(args[i+1]); i++;}
      else if(args[i].substring(0,1).equals(">")) break;
      else if(i != 0) prt("RRPClient unknown switch["+i+"]="+args[i]+" ln="+argline);
    }

    prta(tag+" RRPClient: new line parsed dbg="+dbg+" ring="+filename+" ext="+ext+
            " ip="+ipadr+"/"+port+" forceInit="+forceInit+" bind="+bind+" dbg="+dbg+" dbgbin="+dbgbin+" noevt="+noEvent);
    tag+="RRPC:";  //with String and type
    // Open the RingFileInputer.
    int next=0;
    lastbuf = new byte[4];
    lastbb = ByteBuffer.wrap(lastbuf);
    gotlast=null;
    
    // Figure out where we last recorded our position in the file
    while (in == null) { 
      try {
        gotlast = new RawDisk(filename.trim()+".last"+ext,"r");  
        int n = gotlast.readBlock(lastbuf, 0,4);
        lastbb.position(0);
        lastAckSeq = lastbb.getInt();
        gotlast.close();
      }
      catch(FileNotFoundException e) {
        lastAckSeq = -1;        // It is unknown
      }
      catch(IOException e) {
        prta(tag+" IOException reading .last file="+e.getMessage());
        e.printStackTrace();
        System.exit(1);
      }
      
      // Now reopen it as a read write file
      try {
        gotlast = new RawDisk(filename.trim()+".last"+ext,"rw");
        lastbb.position(0);
        lastbb.putInt(lastAckSeq);
        try{
          if(!hdrdump) gotlast.writeBlock(0, lastbuf, 0, 4);

        }
        catch(IOException e) {
          prta(tag+"IOException initializing .last file e="+e.getMessage());
        }

      }
      catch(FileNotFoundException e) {
        prta(tag+" could not open .last file!!! e="+e.getMessage());
        System.exit(0);
      }
      try {
        in = new RingFileInputer(filename, lastAckSeq);
        prta("RingFile: "+in+" .last"+ext+" lastAckSeq="+lastAckSeq);
        if(lastAckSeq >= 2000000000) lastAckSeq -= 2000000000;
      }
      catch(IOException e) {
        prta(tag+" File not found="+filename);
        SendEvent.edgeSMEEvent("RRPC_NoRing","RRPClient "+filename+" "+ext+" ring file not found","RRPClient");

        //SimpleSMTPThread.email(Util.getProperty("emailTo"),"Ring file not found in RRPClient "+filename,"BODY");
        try{sleep(60000);} catch(InterruptedException e2) {}
        in =null;
      }
      if(forceInit) { lastAckSeq = in.getNextout();}
      if(hdrdump) {
        //prta("RingFile: "+in+" .last lastAckSeq="+lastAckSeq);
        monitor=null;
        shutdown=null;
        return;
      }
    } 
    monitor = new RRPCMonitor(this);
    if(npanic > 0) {prta(tag+" npanic set to "+npanic+" two minute intervals bad");monitor.setPanicCount(npanic);}
    shutdown = new RFTCIShutdown();
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  @Override
  public void terminate() {
    // Set terminate do interrupt.  If IO might be blocking, it should be closed here.
    terminate=true;
    interrupt();
    //in.close();
    close();
  }
  public void close() {
    if(s != null)
      if(!s.isClosed()) 
        try {
          s.close();
        }
        catch(IOException e) {}
  }
  /** this thread reads new data from the Input ring file and sends it onto the 
   *ChannelInfrastructure if the channels is on the MiniSeed list */
  @Override
  public void run()
  { 
    running=true;               // mark we are running
    byte [] hdr = new byte[8];
    ByteBuffer hdrbb = ByteBuffer.wrap(hdr);
    hdrbb.position(0);
    hdrbb.putShort((short) 0xa1b2);
    
    // Open the corresponding Channel infrastructure.
    byte [] buf4096 = new byte[4096];
    byte [] buf512 = new byte[512];
    byte [] buf;
    int seq;
    long lastNbytes=0;
    int npackets=0;
    lastStatus = System.currentTimeMillis();
    int len;
    int force=0;
    int julian2000 = SeedUtil.toJulian(2000, 1, 1);
    int timesBehind=0;
    int nzero=0;
    // Open the connection, this insures the next packet exchange has happend
    prta(tag+" Run() has started - open connection");
    openConnection();
    
    while(true) {
      try {                     // Runtime exception catcher
        if(terminate) break;
        state=1;
        if(s.isClosed()) openConnection();
        state=2;
        try {
          seq = in.getNextout();            // Get sequence # of next block
          state=21;
          len = in.getNextData(buf4096);        // Note: this will block if no data available
          state=22;
          if(terminate || len == -1) break; // File is closed
          if(len != 512) {
            in.setNextout(seq+1);
            prta(" **** got non-512 length="+len+" force seq="+(seq+1));
          }
          state=23;
          if(buf4096[0] == 0 && buf4096[1] == 0 && buf4096[6] == 0 && buf4096[8] == 0 && buf4096[18] == 0) {
            nzero++;
            state=24;
            if(nzero % 1000 == 999) prta(" seq="+seq+" is zero skip.  nzero="+nzero);
            continue;
          }
          if(len == 512 || len == 0) {System.arraycopy(buf4096, 0, buf512, 0, 512); buf=buf512;}
          else buf = buf4096;
          state=3;
          if(delayMS > 0) {
            state=4;
            try {
              int [] time = MiniSeed.crackTime(buf4096);  // get hour, minute, second, hsec
              int [] ymd = SeedUtil.fromJulian(MiniSeed.crackJulian(buf4096));
              int day = SeedUtil.doy_from_ymd(ymd);
              // Note : jan 1, 1970 is time zero so 1 must be subtracted from the day
              long millis = (ymd[0] -1970)*365L*86400000L+(day-1)*86400000L+((long) time[0])*3600000L+
                  ((long) time[1])*60000L+((long)time[2])*1000L;
              millis += ((ymd[0] - 1969)/4)*86400000L;      // Leap years past but not this one!
              if(millis - System.currentTimeMillis() > 600000) continue; // skip data from the future!
              int loop=0;
              while( System.currentTimeMillis() - millis < delayMS) {
                loop++;
                if( (loop % 100) == 10 || (npackets % 1000) == 1)
                  prta("Waiting for delay now="+System.currentTimeMillis()+" til "+millis+" "+
                        (System.currentTimeMillis() - millis)+
                        " "+MiniSeed.crackSeedname(buf4096)+" "+
                        ymd[0]+"/"+ymd[1]+"/"+ ymd[2]+" "+time[0]+":"+time[1]+":"+time[2]);
                try{sleep(1000);} catch(InterruptedException e) {}

              }
            }
            catch(IllegalSeednameException e) {
              prta("Got illegal miniseed block "+MiniSeed.toStringRaw(buf4096));
              e.printStackTrace();
            }
          }
          state=6;
          
          // Send data in buffer to server, make sure its open and sending
          boolean sent=false;
          if(terminate) break;
          while (!sent) {
            if(s.isClosed()) break;

            state=7;
            // Send the data out breaking it into 512 byte blocks.
            for(int off=0; off<buf.length; off+=512) {
              short chksum=0;
              hdrbb.position(4);      // position the sequence and stuff it
              hdrbb.putInt(seq);
              for(int i=0; i<4; i++) chksum += hdr[i+4];    // chksum of the sequence

              // If we are in binary debug mode, put sequence in first part of sequence in MiniSeed
              if(dbgbin) {
                ByteBuffer bbout = ByteBuffer.wrap(buf);
                bbout.position(0); 
                if(forceErrors && seq % 10000 == 6000) {
                  if( force % 2 == 1) bbout.putInt(seq+1); 
                  force++;
                }
                else bbout.putInt(seq);
              }
              for(int i=0; i<512; i++) chksum += buf[i+off]; // chksum of the payload
              hdrbb.position(2);
              if(forceErrors && seq % 10000 == 2000 ) {
                if(force % 2 == 0) chksum++;
                force ++;
              }
              hdrbb.putShort(chksum);

              try {
                state=9;
                sockout.write(hdr, 0, 8);
                state=10;
                sockout.write(buf, off, 512);
                state=11;
                npackets++;
                if(dbg || npackets % 10000 == 0) 
                  prta("DBG: Send seq ="+seq+" in.nextout+"+in.getNextout()+" chksum="+chksum);
                inbytes += 520;
                try {
                  int [] time = MiniSeed.crackTime(buf);
                  if(time[0] >= 24 || time[1] >=60 || time[2] >= 60) prta("Bad Time out="+MiniSeed.toStringRaw(buf));
                  if(dbg && npackets % 10000 == 0) 
                    prta(tag+"write "+seq+" "+in.getLastSeq()+" off="+off+" l="+len+" #pkt="+npackets+" "+MiniSeed.toStringRaw(buf));
                }
                catch(IllegalSeednameException e) {
                  prta("Got illegal miniseed!seq="+seq+" lseq="+in.getLastSeq()+" off="+off+" l="+len+" "+MiniSeed.toStringRaw(buf));
                  in.setNextout(seq+1);
                  break;
                }
              }
              catch(IOException e) {
                Util.SocketIOErrorPrint(e,tag+" RRPC: writing to socket ",getPrintStream());
                prta(tag+"Closing socket to start again. seq="+seq);
                e.printStackTrace(getPrintStream());
                close();
                try {sleep(30000);} catch(InterruptedException e2) {}
                break;          // bail out of this loop, let connection open and seq exchange start again
              }
            }
            state=8;
            sent=true;
          }           // while not sent
          
          // Is it time for status yet
          if( System.currentTimeMillis() - lastStatus > 600000) {
            prta(tag+"#pkt="+npackets+"  nKB="+( inbytes - lastNbytes)/1000+" kbps="+
                    ((inbytes - lastNbytes + 500)*8/600000)+" Ring:"+in);
            if(in.getLastSeq() - in.getNextout() > behindAlertLimit && behindAlertLimit> 0) {
              if(!noEvent)
                SendEvent.edgeSMEEvent("RRPBehind", logname+" "+filename+" "+ext+" behind "+
                        (in.getLastSeq() - in.getNextout() + 500)/1000+" Kpkt /"+behindAlertLimit, this);
                      

              if(in.getLastSeq() - in.getNextout() > 1000) {
                timesBehind++;
                if(timesBehind % 6 == 2) {
                  prta(" **** behind more than 1000 packets for more than 20 minutes.  Remake the connection..."+timesBehind);
                  if(!noEvent)
                    SendEvent.edgeSMEEvent("RRPBehindTO", logname+" "+filename+" "+ext+" behind "+
                            (in.getLastSeq() - in.getNextout() + 500)/1000+" Kpkt", this);
                  s.close();
                }
              }
            } else timesBehind=0;
            lastStatus = System.currentTimeMillis();
            lastNbytes = inbytes;
            Util.loadProperties("edge.prop");
            /*if(!edgeDigit.equals(EdgeThread.EdgeThreadDigit())) {
              Util.setTestStream("rrpc.log"+EdgeThread.EdgeThreadDigit());
              edgeDigit=EdgeThread.EdgeThreadDigit();
            }*/
          }
        }
        catch(IOException e) {
          prta(tag+" IOException reading from ringfile ="+e.getMessage());
          e.printStackTrace();
        }
      }
      catch(RuntimeException e) {
        prta(tag+" RuntimeException in ringtochan "+this.getClass().getName()+" e="+e);
        if(getPrintStream() != null) e.printStackTrace(getPrintStream());
        else e.printStackTrace();
        if(e.getMessage() != null )
          if(e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeSMEEvent("OutOfMemory",logname+" RRPClient out of memory "+filename+" "+ext, this);
            /*SimpleSMTPThread.email(Util.getProperty("emailTo"),
                "Out of Memory in "+this.getClass().getName()+" on "+IndexFile.getNode(),
                Util.asctime()+" "+Util.ascdate()+" Body");*/
          throw e;
        }   
      }
    }       // while(true) open on ring file
    //monitor.terminate();
    prta(tag+" is terminated!");
    try{Thread.sleep(4000);} 
    catch(InterruptedException e) {
      prta(tag+" Got interruped while exiting!"); 
      try{Thread.sleep(4000);}catch(InterruptedException e2) {}
    }
    prta(tag+" threads1="+Util.getThreadsString());
    try{Thread.sleep(4000);}catch(InterruptedException e2) {}
    prta(tag+" threads2="+Util.getThreadsString());
    running=false;
    terminate=false;
  }
  private void openConnection() {
    long lastEmail=0;
    if(s == null || s.isClosed() || !s.isConnected()) {
      int loop=0;
      int isleep=7500;
      int nbad=0;
      while(true) { 
        if(terminate) break;
        try
        {
          prta(tag+"OC: Open Port="+ipadr+"/"+port+" bind="+bind+" closed="+(s != null?""+s.isClosed():"Null")+" s="+s);
          InetSocketAddress adr = new InetSocketAddress(ipadr,port);
          if(s != null) 
            if(!s.isClosed()) {
              try{
                prta(tag+"OC: close existing socket");
                s.close();
              } catch(IOException e) {}
            }
          prta(tag+"OC: New Socket bind="+bind);
          s = new Socket();
          if(bind != null)
            if(!bind.equals(""))
              s.bind(new InetSocketAddress(bind, 0));  // User specified a bind address so bind it to this local ip/ephemeral port
          s.connect(adr);
          //s = new Socket(ipadr, port);
          prta(tag+"OC: new socket is "+s);
          sockin = s.getInputStream();
          sockout = s.getOutputStream();
          if(ackReader == null) ackReader = new AckReader();
          else ackReader.resetReadOne();
          prta(tag+"OC: Wait for response from server");
          loop=0;
          while(!ackReader.getReadOne()) {
            try {
              sleep(100);
              loop++;
              if(loop > 1200) {
                prta(tag+"OC: No response from server for 120 seconds.  Close and reopen");
                s.close();
                break;
              }
            }
            catch(InterruptedException e) {}
          }
          if(s.isClosed()) continue;      // No open socket, try again
          prta(tag+"OC: Server responded lastAckSeq="+lastAckSeq);
          break;
        }
        catch (UnknownHostException e)
        {
          prt(tag+"OC: Host is unknown="+ipadr+"/"+port+" loop="+loop);
          if(loop % 120 == 0) {
            SendEvent.edgeSMEEvent("RRPC_HostBad", "RRPClient host unknown="+ipadr+" "+filename+" "+ext, this);
            //SimpleSMTPThread.email(Util.getProperty("emailTo"),"RRPClient host unknown="+ipadr,
            //  "This message comes from the RRPClient when the host computer is unknown,\nIs DNS up?\n");

          }
          loop++;
          try {sleep(30000L);} catch(InterruptedException e2) {}
        }
        catch (IOException e) 
        {
          if(e.getMessage().equalsIgnoreCase("Connection refused")) {
            isleep = isleep * 2;
            if(isleep >= 360000) isleep=360000;      // limit wait to 6 minutes
            prta(tag+"OC: Connection refused.  wait "+isleep/1000+" secs ....");
            if(isleep >= 360000 && System.currentTimeMillis() - lastEmail > 3600000) {
              if(!noEvent)
                SendEvent.edgeSMEEvent("RRPC_Refused", "RRPClient refused="+ipadr+"/"+port+" "+filename+" "+ext, this);
              /*SimpleSMTPThread.email(Util.getProperty("emailTo"),tag+" RPP "+ipadr+"/"+port+" repeatedly refused",
                  Util.ascdate()+" "+Util.asctime()+" from "+filename+"\n"+
                    "This message comes from the RPPClient when a connection is repeatedly refused,\n"+
                    "Is remote server up?  This message will repeat once per hour.\n");*/
              lastEmail = System.currentTimeMillis();
            }
            try {sleep(isleep);} catch(InterruptedException e2) {}
          }
          else if(e.getMessage().equalsIgnoreCase("Connection timed out")) {
            prta(tag+"OC: Connection timed out.  wait "+isleep/1000+" secs ....");
            if(isleep >= 300000 && System.currentTimeMillis() - lastEmail > 3600000) {
              if(!noEvent)
                SendEvent.edgeSMEEvent("RRPC_HostBad", "RRPClient timed out ="+ipadr+"/"+port+" "+filename+" "+ext, this);

              /*SimpleSMTPThread.email(Util.getProperty("emailTo"),tag+" RPP "+ipadr+"/"+port+" repeatedly timed out",
                  Util.ascdate()+" "+Util.asctime()+" from "+filename+"\n"+
                    "This message comes from the RPPClient when a connection is repeatedly timing out,\n"+
                    "Is remote server up?  This message will repeat once per hour.\n");*/
              lastEmail = System.currentTimeMillis();
            }
            try {sleep(isleep);} catch(InterruptedException e2) {}
            isleep = isleep * 2;
            if(isleep >= 300000) isleep=300000;
          }
          else  {
            prta(tag+"OC: connection nbad="+nbad+" e="+e);
            e.printStackTrace(getPrintStream());
            Util.IOErrorPrint(e,tag+"OC: IO error opening socket="+ipadr+"/"+port);
            try {sleep(120000L);} catch(InterruptedException e2) {}
            if(nbad++ % 10 == 9) {
              if(!noEvent)
                SendEvent.edgeSMEEvent("RRPCBadConn", "Bad connection repeatedly to "+ipadr+"/"+port+" "+filename+" "+ext, "RRPClient");
              Util.exit(1);
            }
          }

        } 
        catch(RuntimeException e) {
          prta(tag+"OC: runtime caught e="+e);
          e.printStackTrace(getPrintStream());
        }
      }   // While True on opening the socket
    }     // if s is null or closed  
    if(dbg) prta(tag+"OC: OpenConnection() is completed.");
  }
  
    /** return the monitor string for Nagios
   *@return A String representing the monitor key value pairs for this EdgeThread */
  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }
  @Override
  public StringBuilder getStatusString() {return  statussb;}
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;} //  we use prt directly
  
  final class AckReader extends Thread {
    boolean readOne;
    public AckReader() {
      readOne=false;
      setDaemon(true);
      start();
    }
    public void resetReadOne() {readOne=false;}
    public boolean getReadOne() {return readOne;}
    private void sendNull() {
      byte [] b = new byte[12];
      ByteBuffer bb = ByteBuffer.wrap(b);
      bb.position(4);
      bb.putInt(-1);
      bb.putInt(lastAckSeq);
      short chksum=0;
      for(int i=0; i<8; i++) chksum += b[4+i];    // csum on seq and lastAckSeq only
      bb.position(0);
      bb.putShort((short) 0xa1b2);
      bb.putShort(chksum);
      try {
        s.getOutputStream().write(b, 0, 12);
      } 
      catch(IOException e) {
        prta(tag+"ACKR: IOError writing null.  Close socket.");
      }
      prta(tag+"ACKR:  Send Null for ack="+lastAckSeq);
    }
    @Override
    public void run() {
      byte [] b = new byte[12];
      ByteBuffer bb = ByteBuffer.wrap(b);
      while(s == null) try{sleep(1000);} catch(InterruptedException e) {}
      long lasttime = System.currentTimeMillis();
      int lastnextout = in.getNextout();
      long now;
      while(!terminate) {
        // if the socket is closed, wait for it to open again
        while(s == null) try{sleep(1000);} catch(InterruptedException e) {}
        while((s.isClosed() || !s.isConnected()) && !terminate) try {sleep(1000);} catch(InterruptedException e) {}
        if(terminate) continue;
        int len = 12;
        int l=0;
        int nchar;
        while (len - l > 0) {
          try{
            while( s.getInputStream().available() <= 0 && !terminate) 
              try{sleep(10);} catch(InterruptedException e) {}
            if(terminate) break;
            nchar = s.getInputStream().read(b, l, len -l);
          }
          catch(IOException e) {
            if(e.toString().contains("Socket is closed") || e.toString().contains("not connect")) {
              prta("Socket closed by server! - reconnect. e="+e);
            }
            else {
              Util.SocketIOErrorPrint(e,tag+"ACKR: IOError reading ack socket. close socket", getPrintStream());
              prta(tag+"IOError reading from ack socket.");
              e.printStackTrace(getPrintStream());

            }
            if(s != null)
              if(!s.isClosed()) close();            
            break;
          }
          if(terminate) break;
          if(nchar < 0) {      // EOF, socket is closing
            prt(tag+"ACKR: EOF reading from AckReader socket close socket");
            if(s != null)
              if(!s.isClosed()) close();
            break;
          }
          l += nchar;
        }
        if(s.isClosed()) {
          try{sleep(10000);} catch(InterruptedException e) {}
          continue;
        }      // socket is now closed, wait for a new one
        if(terminate) break;
        bb.position(0);
        short leads=bb.getShort();
        if(leads != (short) 0xa1b2) {
          prta(tag+"ACKR: Leadin not right reading acks - close up socket leads="+Util.toHex(leads));
          close();
          continue;
        }
        
        short flags = bb.getShort();
        int seqAck = bb.getInt();
        int last = bb.getInt();
        now = System.currentTimeMillis();
        if(now - lasttime > 30000) {
          long bps = ((in.getNextout() - lastnextout)*520 + 255)*8/Math.max(1,now - lasttime);
          lasttime=now;
          lastnextout = in.getNextout();
          prta(tag+" got seqAck="+seqAck+" last="+last+" lastAckSeq="+lastAckSeq+" kb/s="+bps+" in.nextout="+in.getNextout()+" in.last="+in.getLastSeq());
        }
        if(forceAckSeq >=0) {seqAck=forceAckSeq; last=forceAckSeq; forceAckSeq=-2;prta(tag+" ***** force sequence to "+last);}
        // Check for far end wanting a resstart somewhere else
        if(dbg) prta("ACKR: rcv seq="+seqAck+" last="+last+" lastAckSeq="+lastAckSeq);
        try {
          if(lastAckSeq == -1) {
            prta("ACKR: *** lastAckSeq == -1 means new ring file.  Send null to force reset. acked="+seqAck+" last="+last);
            sendNull();
            //lastAckSeq=0;
            last=0;
            in.setNextout(in.getLastSeq());
          }
          else if(seqAck == -1) {    // The user wants to start at an offset from our last known
            int next = lastAckSeq - last;
            if(next < 0) next += 2000000000;
            if(in.setNextout(next)) prta(tag+"ACKR:  Startup mode with offset="+last+" sets nextout successfully to "+next);
            else {
              prta(tag+"ACKR: Startup mode with offset="+last+" was NOT SUCCESSFUL.  Set to latest data="+in.getNextout());
              lastAckSeq=in.getNextout();
              sendNull();
            }
            lastAckSeq=in.getNextout();
            readOne=true;
          }
          else if(seqAck == -2) {    // The user does not know, but wants all possible data
            if(in.getLastSeq() == 0) {
              prta(tag+"ACKR: Startup mode for Maximum block, but input files is likely empty last="+in.getLastSeq()+" Set nextout to zero");
              in.setNextout(in.getLastSeq());
              lastAckSeq=-1;
              last=0;
            }
            else {
              int next = in.getNextout() - in.getSize()+in.getSize()/100;
              if(in.getNextout() < in.getSize()) next=1;      // It must be a new file do not trust the old data
              if(next < 0) next += 2000000000;
              prta(tag+"ACKR: Startup mode maximum blocks set to "+next+" old nextout="+in.getNextout()+" size="+in.getSize());

              in.setNextout(next);
              lastAckSeq=in.getNextout();
            }
            sendNull();
            readOne=true;
          }
          else if(seqAck == last) {     // Initialize on this sequence
            lastAckSeq=seqAck;
            if(lastAckSeq >= 2000000000) lastAckSeq -=2000000000;
            prta(tag+" attempt to set sequence ="+lastAckSeq+" in Ring.  next="+in.getLastSeq()+" size="+in.getSize()+" nextout="+in.getNextout());
            if(forceInit) {lastAckSeq = in.getLastSeq();in.setNextout(in.getLastSeq()); prta(tag+" ForceINIT on - set sequence to "+in.getNextout()); sendNull();}
            else if( in.setNextout(lastAckSeq)) {
              prta(tag+"ACKR: Startup set seq to "+seqAck+" was successful "+in.getNextout());
              if(forceAckSeq == -2) {   // When we are forcing an Ack we must be forcefull and set lastAckSeq and do a sendNull
                lastAckSeq = in.getNextout();
                sendNull();
              }
            }
            else {
              int next = in.getNextout() - in.getSize()+in.getSize()/100;
              if(in.getNextout() < in.getSize()) next=1;  //It must be a new file do not trust the old data
              if(next < 0) next += 2000000000;
              in.setNextout(next);
              prta(tag+"ACKR: Startup set seq OOR - set it to oldest nout="+in.getNextout()+" size="+in.getSize()+" new="+next);
              lastAckSeq=in.getNextout();
              sendNull();
            }

            readOne=true;
          }
          else if(seqAck != lastAckSeq+1) {       // There is a gap in the acknowlegements!
            prta(tag+"ACKR: Got gap in acks lastAckSeq="+lastAckSeq+" got "+seqAck+" new last="+last);
          }
          else if( last / 10000 != seqAck / 10000) prta(tag+"ACKR: status lastAckSeq="+lastAckSeq+" got "+seqAck+" new last="+last);
          lastAckSeq = last;

          // Update file with last acknowledge.
          lastbb.position(0);
          lastbb.putInt(last);

          gotlast.writeBlock(0, lastbuf, 0, 4);
        }
        catch(IOException e) {
          prta(tag+"ACKR: Wow.  Error writing the lastBlock file!"+e.getMessage());
          Util.exit(1);
        }
        
      }
      prta(tag+"ACKR: is terminated");
    }
  }
  
  
  /** RRPCMonitor the RRPClient and stop it if it does not receive heartBeats or data! */
  final class RRPCMonitor extends Thread {
    //boolean terminate;        // If true, this thread needs to exit
    int msWait;               // user specified period between checks
    boolean interruptDone;    // Flag that we have gone off
    RRPClient thr;      // The thread being RRPCMonitored
    long lastInbytes;         // count of last to look for stalls
    int npanic=10;
    public boolean didInterrupt() {return interruptDone;}
    public void terminate() {terminate=true; interrupt();}
    public void setPanicCount(int n) {npanic=n;}
    public RRPCMonitor(RRPClient t) {
      thr = t;
      setDaemon(true);
      msWait=120000;      // Set the ms between checks
      start();
    }
    @Override
    public void run() {
      long lastNbytes = 0;
      int panic=0;
      prta(tag+"RRPCMonitor has stared msWait="+msWait);
      int loop=0;
      long diff;
      //try{sleep(msWait);} catch(InterruptedException e) {}
      while(!terminate) {
        int ms=0;
        while(ms < msWait) {
          try{sleep(Math.min(msWait - ms, 2000));} catch(InterruptedException e) {}
          ms += 2000;
          if(terminate) break;
        }
        diff=inbytes - lastNbytes;
        //prta(tag+" LCM: HB="+heartBeat+" lHB="+lastHeartbeat+" in ="+inbytes+" lin="+lastInbytes);
        if(diff < 1000) {  
          thr.interrupt();      // Interrupt in case its in a wait
          thr.close();
          if(npanic < 500)  // If its > 500, then this is a RRP that is not expected to have continuous flow.
            if(!noEvent && panic > 2)
              SendEvent.edgeSMEEvent("RRP_Stalled","RRPClient "+filename+" "+ext+" has timed out",this);

            
          // Close the ring file
          lastNbytes = inbytes;
          interruptDone=true;     // So interrupter can know it was us!
          prta(tag+" RRPCMonitor has gone off panic="+panic+"/"+npanic+" s="+s+" isclosed="+s.isClosed()+" isalive="+thr.isAlive()+" #b="+lastNbytes+""
                  + " state="+state+" "+thr);
          if(!thr.isAlive()) {
            prta(tag+" RRPCMonitor no active thread.  exit()");
            Util.exit(2);
          }
          //try{sleep(msWait);} catch(InterruptedException e) {}  // What is this extra wait for?
          interruptDone=false;
          panic++;
          if(panic > (lastNbytes < 3000?Math.max(npanic,500):npanic)){  // no data has really come in
            if(!noEvent)
              SendEvent.edgeSMEEvent("RRP_Panic","RRPClient "+filename+" "+ext+" has paniced and is exiting.", this);
            Util.exit("Panic interval "+panic+"/"+npanic);
          }
        }
        else panic=0;
        lastNbytes=inbytes;
        loop++;
        if(loop % 10 == 2) prta(tag+" RRPCMonitor status loop="+loop+" diff="+diff+" panic="+panic+" "+thr);
      }
      prta(tag+" RRPCMonitor is exiting.");
    }
  }
 /** this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down.  This must cause the thread to exit
   */
  class RFTCIShutdown extends Thread {
    /** default constructor does nothing the shutdown hook starts the run() thread */
    public RFTCIShutdown() {
    }
    
    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(tag+"RFTCI Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop=0;
      //terminate();
      try{sleep(2000);} catch(InterruptedException e) {}
      prta(tag+"RFTCI shutdown() is complete.");
      //Util.exit(0);
    }
  }  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // TODO code application logic here
    Util.setProcess("RRPClient");
    EdgeProperties.init();
    boolean makeCheck=true;
    String logfile="rrpc";
    EdgeThread.setMainLogname("rrpc");
    Util.setModeGMT();
    //EdgeMom.setNoConsole(false);
    //EdgeMom.prt("no console");
    //EdgeThread.setUseConsole(true);
    boolean dbg=false;
    Util.setNoInteractive(true);
    String argline = "";
    for(int i=0; i<args.length; i++) {
      argline += args[i]+" ";
      if(args[i].equals("-dbg")) dbg=true;
      if(args[i].equals("-hdrdump")) EdgeThread.setUseConsole(true);
      if(args[i].equals("-log")) logfile=args[i+1];
    }
    if(args.length == 0) {
      System.out.println("RRPClient -ring file [-hdrdump] -ip nn.nn.nn.nn [-p port][[-dbg][-dbgbin]");
      System.out.println("   -ring  filename the ring file to operate on.  defautl='ringbuffer'");
      System.out.println("   -hdrdump            Dump out the header information from the ring and the .last file");
      System.out.println("   -ip    nn.nn.nn.nn IP address of the server");
      System.out.println("   -p     port         User port instead of default of 22223");
      System.out.println("   -dbg                More verbose output");
      System.out.println("   -dbgbin             Replace bytes of miniseed with seq number for testing mode (DEBUG ONLY)");
      System.exit(0);
    }
    argline+= " >>"+logfile;
    // -host n.n.n.n -port nn -mhost n.n.n.n -mport nn -msgmax max -qsize nnnn -wait nnn (secs) -module n -inst n
    //HydraOutputer.setCommandLine("");     // set all defaults  
    RRPClient infra=new RRPClient(argline,"RRPC");

  }
  
}
