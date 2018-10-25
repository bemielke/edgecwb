/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.ewexport;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.ew.EWMessage;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/** This class empties a TraceBufQueue which is filled by a EWExportSocket thread.  This
 * actually sends the data, limits the bandwidth usage to avoid going too fast for EW rings
 * in a catch up situation.
 * 
 * It starts the EWHeartBeatHandler which sends out heartbeats at the prescribed rate, and
 * receives heartbeats and implements the heart beat timeout when heart beats in do not arrive.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class EWExportOutputer extends Thread {
  //private static boolean DEBUGDISABLE;
  //private static DecimalFormat df3= new DecimalFormat("000");
  private static final int EXPECTING_START=1;
  private static final int SEARCHING_START=2;
  private static final int IN_MESSAGE=3;
  private static final byte STX=2;
  private static final byte ETX=3;
  private static final byte ESC=27;
  private static final long BYTES_LONG_TERM=2000000;
  public static final Integer mutex = Util.nextMutex(); // create a mutexable for writing
  private long bytesOut;
  private long bytesIn;
  private long lastBytesOut;
  private long lastBytesIn;
  private int npackets;
  private int throttleMS;
  private final int orgThrottleMS;
  private long lastThrottle;
  private int throttleBytes;
  private long totalThrottleBytes;
  private long lastThrottleAvg;
  private int avgThrottleMS;
  private int nthrottle;
  private long totthrottle;
    
  private int skipped,nsyncError, ndisconnect;
  private final EWHeartBeatHandler hbHandler;
  private boolean running;
  private int state;
  private boolean terminate;
  private boolean clientMode;
  private int instate;
  private final EdgeThread par;
  private Socket s;
  private int lastAckSeq=-1;
  int seqLimit=100;
  long latencySum;
  int nlatency;

  private final TraceBufQueue queue;
  boolean scnMode;
  private boolean dbgdata;
  private boolean dbg;
  private final int hbInstitution, hbModule, hbInterval, hbIntervalTimeout;
  private final String hbMsgOut;
  private final String hbMsgIn;
  private final String tag;
  private final int exportPort;
  private final String exportHost;
  private final boolean doAcks;
  private final EWExportSocket ewsocket;
  private long avglat;
  public long getBytesIn() {return bytesIn;}
  public long getBytesOut() {return bytesOut;}
  public boolean isRunning() {return running;}
  long lastPackets;
  long lastStatus;
  public String getStatus() { 
    long now = System.currentTimeMillis();
    long elapse = now - lastStatus;
    avglat = latencySum/Math.max(nlatency,1); 
    nlatency=0;
    latencySum=0;
    long nbi = bytesIn - lastBytesIn;
    long nbo = bytesOut - lastBytesOut;
    long bps = nbo*8000/Math.max(elapse,1);
    lastBytesIn = bytesIn;
    lastBytesOut = bytesOut;
    lastStatus=now;
    String t = tag+" in="+nbi+" out="+(nbo/1000)+" kB "+bps+" b/s avglat="+avglat+" state="+state+"/"+instate+
            " thrMS="+throttleMS+" org="+orgThrottleMS+" avgThrMS="+avgThrottleMS+" "+
            (400000000/Math.max(avgThrottleMS,1))+" b/s alive="+isAlive();
    
    return t;
  }
  long lastMonBytesIn;
  long lastMonBytesOut;
  long lastMonTime=System.currentTimeMillis();
  public String getMonitorString() {
    long elapse = System.currentTimeMillis() - lastMonTime;
    lastMonTime=System.currentTimeMillis();
    long nbi = bytesIn - lastMonBytesIn;
    long nbo = bytesOut -  lastMonBytesOut;
    lastMonBytesIn = bytesIn;
    lastMonBytesOut = bytesOut;
    String tg = tag.replaceAll("\\[", "").replaceAll("\\]", "").trim()+"-";
    return tg+"BytesIn="+nbi+"\n"+tg+"KBPSOut="+(nbo/elapse)+"\n"+tg+"BytesOut="+nbo+
            "\nQUsed="+queue.used()+"\nConnect="+
            (s == null?"0":(s.isClosed()?"0":s.getInetAddress().toString().substring(1)))+"\n";
  }


  @Override
  public String toString() {return tag+" "+exportHost+"/"+exportPort+" #pck="+npackets+" #disconn="+ndisconnect+" Qsize="+queue.getSize()+
          " sq="+queue.getTailSeq()+" thrMS="+throttleMS+" avgThrMS="+avgThrottleMS+" "+400000000/avgThrottleMS+" bits/s";}
  public void setDebug(boolean t) {dbg=t;}
  public void terminate() {terminate=true; } //  This stuff probably in run()????
  /**
   * 
   * @param q
   * @param inst
   * @param mod
   * @param msgout
   * @param msgin
   * @param hbint
   * @param hbto
   * @param host
   * @param port
   * @param acks
   * @param tg
   * @param scn
   * @param parent
   * @param sck
   * @param throtMS Number of milliseconds for each 50,000 bytes (400 bits/throtMS) = bandwidth in mbits/s
   */
  public EWExportOutputer(TraceBufQueue q, int inst, int mod, String msgout, String msgin, int hbint, int hbto,
          String host, int port, boolean acks, String tg, int throtMS, boolean scn, EdgeThread parent, EWExportSocket sck) { 
    queue = q;
    par = parent;
    hbInstitution = inst;
    hbModule = mod;
    hbInterval = hbint;
    hbIntervalTimeout = hbto;
    hbMsgOut = msgout;
    hbMsgIn = msgin;
    exportHost = host;
    exportPort = port;
    ewsocket=sck;
    throttleMS = throtMS;
    orgThrottleMS = throtMS;
    scnMode=scn;
    doAcks = acks;
    if(port > 0) clientMode=true;
    tag = tg.substring(0,tg.length()-1)+"Out]";
    par.prta(tag+" EWExportOutputer: INIT host="+host+" port="+port+" inst="+inst+" mod="+mod+" msgin="+msgin+" msgout="+msgout+
            " hbint="+hbint+" hbto="+hbto+" throtMS="+throtMS+" q="+queue+" scn="+scnMode+" "+this.getName());
    hbHandler = new EWHeartBeatHandler((byte) hbInstitution, (byte) hbModule, hbMsgOut,  hbInterval, hbIntervalTimeout);
    seqLimit = Math.min(250, queue.getSize());
    lastStatus=System.currentTimeMillis();
    start();
  }
  @Override
  public void run() {
    running=true;
    int npack=0;
    byte [] output = new byte[4096+70+4000];  // Allow room for escaping a lot of characters
    int offset;
    int navgcycle=0;
    int seq= -1;
    long lat;
    int idleLoop=0;
    while(!terminate) {
      if(exportPort > 0 && s == null) openSocket();
      if(queue == null) continue;
      try {
        TraceBuf tb = queue.getTail();

        if(tb == null || s == null) {
          try{
            sleep(100);
          } 
          catch(InterruptedException e) {} 
          if(s == null) {
            idleLoop++;
            if(idleLoop % 6000 == 10) par.prta(tag+" Idle, waiting for connection loops="+idleLoop+" s="+s);
          }
        }
        else {
          if(seq == -1) 
            seq = queue.getTailSeq() % seqLimit;
          lat = System.currentTimeMillis() - tb.getNextExpectedTimeInMillis() - (int) (1000/tb.getRate()+.1);
          if(scnMode) tb.makeSCNFromSCNL();  // Convert to SCN
          latencySum += Math.min(lat,300000);
          nlatency++;
          if(dbg) par.prta(tag+" processing sq="+queue.getTailSeq()+" "+npack+" lat="+lat+"ms tb=" + tb.toString());
          npack++;
          output [0] = STX;
          offset=1;
          // The LOGO needs to be encoded into ASCII
          // IF acks are on build the packet with acks in it
          if(doAcks) {
            output[1]='S'; output[2]='Q'; output[3]=':';
            toASCII(seq, output,4);
            offset=7;
            if(++seq >= seqLimit) 
              seq=0;
          }
          int len = binEscape(tb.getBuf(),6, tb.getNsamp()*4+70, output, offset+9);
          toASCII(hbInstitution, output, offset);
          toASCII(hbModule, output, offset+3);
          toASCII((int) tb.getBuf()[1], output, offset+6);
          output[len++] = ETX;
          if(npack % 100 == 0 ) {
            if(EWExport.dbgDataExport.contains(exportHost.trim())) {dbgdata=true;dbg=true;par.prt("Turning out debugging");}
            else {dbgdata=false; dbg=false;}

          }
          if(npack % 10000 == 0 && dbg) {
            ewsocket.logStatus(npack);
          }
          try {
            synchronized(mutex) {
              if(s == null) {par.prt(tag+"**  s is now null.  Connection must be broken");continue;}
              s.getOutputStream().write(output, 0, len);
            }
            bytesOut += len;
            throttleBytes += len;
            try {
              ewsocket.checkInorder(tb, queue.getTailSeq(), true);// Only put in order if it got out
            }
            catch(RuntimeException e) {
              if(e != null) if(e.toString().contains("Packet from future")) {
                queue.bumpTail();
                par.prta("Got packet from the future tb="+tb.toString());
                continue;
              }
            }
            queue.bumpTail();     // Only bump the sequence sent if it is successfully queue out
            if(throttleBytes > 50000) {
              long elapsed = System.currentTimeMillis() - lastThrottle;
              if( elapsed < throttleMS) {
                par.prta(tag+" EWEO: throttling="+Math.max(1L, throttleMS - elapsed)+"/"+throttleMS+" seq="+queue.getTailSeq());
                try {sleep(Math.max(1L, throttleMS - elapsed));} catch(InterruptedException e) {}
                nthrottle++;
                totthrottle += Math.max(1L, throttleMS - elapsed);
              }
              totalThrottleBytes += throttleBytes;
              throttleBytes = 0;
              lastThrottle = System.currentTimeMillis();
              if(ewsocket.isCatchingUp()) {
                //par.prta(tag+" EWEO: is catching up, disable average computation");
                totalThrottleBytes = 0;
                lastThrottleAvg = lastThrottle;
              }
              else {
                if(totalThrottleBytes > BYTES_LONG_TERM && (totalThrottleBytes % BYTES_LONG_TERM) <=50000) {
                  int avg = (int) (8000*BYTES_LONG_TERM/(lastThrottle-lastThrottleAvg)); // bits per second
                  int avgMS = 40000000/avg;        // 50,000 bytes * 8 bits * 1000 ms/s/ avg bits/sec / 10 time real time
                  if(avgThrottleMS <= 0) avgThrottleMS=1;    // it is the first time, mark it so and wait for another
                  else {    // if its not the first time
                    if(avgThrottleMS == 1) avgThrottleMS=avgMS;   // FIrst time, so seed the avg with the first computed avg
                    avgThrottleMS = (avgThrottleMS*19+avgMS)/20;
                    lastThrottleAvg = lastThrottle;
                    par.prta(tag+" Throttle sq="+queue.getTailSeq()+" Avg:="+avg+" bits/s "+avgMS+
                            " ms new 10X estimate, new avg 10x="+avgThrottleMS+
                            " curr  throtMS="+throttleMS+
                            " New="+Math.max(orgThrottleMS, Math.min(avgThrottleMS,orgThrottleMS*4))+" navg="+navgcycle);
                    if(orgThrottleMS > avgThrottleMS) {
                      par.prt(tag+" **** The throttle band width is too small orgMS="+orgThrottleMS+" >"+avgThrottleMS+
                              " ms which would be 10 times real time. recommend "+(400000/avgThrottleMS)+" kbps");
                      if(navgcycle %  1000 == 999) 
                        SendEvent.edgeSMEEvent("ExportBWLow",tag+" Bandwidth too small recommend "+(400000/avgThrottleMS), this);
                    }
                    if(throttleMS > 0 && navgcycle++ > 10) 
                      throttleMS = Math.max(orgThrottleMS, Math.min(avgThrottleMS, orgThrottleMS*4));
                    if(avglat > 120000) {
                      par.prt(tag+" **** latency high - set throttle to orginal/2 ");
                      throttleMS=orgThrottleMS/2;
                    }
                  }
                }
              }
            }

          }
          catch(IOException e) {
            if((""+e).contains("Connection reset")) par.prta(tag+" ** err=Connection reset - close socket");
            else if((""+e).contains("Pipe broken")) par.prta(tag+" ** err=Pipe broken - close socket");
            else if((""+e).contains("Socket close")) par.prta(tag+" ** err=Socket closed - close socket");
            else {
              par.prta(tag+" ** err="+e);
              e.printStackTrace(par.getPrintStream());
            }              
            if(s != null)
              if(!s.isClosed()) try {s.close();} catch(IOException e2) {}
            s=null;
          }
        }
      }
      catch(RuntimeException e) {
        e.printStackTrace(par.getPrintStream());
        par.prta(tag+"**** weird runtime exception!");
        queue.bumpTail();     //bump the sequence sent If we got one runtime we would get another on the same packet
      }
    }
    if(s != null) 
      if(!s.isClosed()) try{s.close();} catch(IOException e) {}
    s = null;
    running=false;
    par.prta(tag+"  is exiting");
    hbHandler.terminate();
  }
  private void toASCII(int inbyte,  byte[] b, int offset) {
    int i = inbyte;
    i = i & 255;
    for(int j=0; j<3; j++) {
      b[2-j+offset] = (byte) (48+ (i % 10));
      i = i/10;
    }
  }
  /** escape out all of the stx, etx and esc in the buffer
   *
   * @param in Input buffer of raw binary bytes
   * @param inoff The offset in in to do first (goes into out[offset]
   * @param len The length of in including the offset
   * @param out The output array of bytes
   * @param offset The offset in out to start
   * @return The total bytes in out
   */
  private int binEscape(byte [] in,int inoff, int len, byte [] out, int offset) {
    int j=offset;
    for(int i=inoff; i<len; i++) {
      if(in[i] == STX || in[i] == ETX || in[i] == ESC) out[j++] = ESC;
      out[j++] = in[i];
    }
    return j;
  }
  private void openSocket() {
    int loop=0;
    int isleep=10000;
    int sleepMax=120000;
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
          par.prta(tag+"* Connection refused.  wait "+(isleep/1000)+" secs ....");
          try {sleep(isleep);} catch(InterruptedException e2) {}
          isleep = isleep * 2;
          if(isleep > sleepMax) isleep=sleepMax;
          if(isleep >= sleepMax) {
            SendEvent.edgeSMEEvent("EXPORTBadCon", "Conn refused "+tag+" "+exportHost+"/"+exportPort, this);
          }
          try {sleep(Util.isNEICHost(exportHost) ? 30000:isleep);} catch(InterruptedException e2) {}
        }
        else if(e.getMessage().equalsIgnoreCase("Connection timed out") ||
                e.getMessage().equalsIgnoreCase("Operation timed out")) {
          par.prta(tag+"* Connection timed out.  wait "+isleep/1000+" secs ....");
          SendEvent.edgeSMEEvent("EXPORTBadCon", "Conn timeout "+tag+" "+exportHost+"/"+exportPort, this);
          try {sleep(isleep);} catch(InterruptedException e2) {}
          isleep = isleep * 2;
          if(isleep > sleepMax) isleep=sleepMax;
          try {sleep(Util.isNEICHost(exportHost) ? 30000:isleep);} catch(InterruptedException e2) {}
        }
        else  {
          //Util.IOErrorPrint(e,tag+"** IO error opening socket="+exportHost+"/"+exportPort);
          par.prta(tag+"** IOError opening socket to "+exportHost+"/"+exportPort+" e="+e);
          try {sleep(120000L);} catch(InterruptedException e2) {}
        }

      }
      if(lastAckSeq != -1 && queue != null) resetTailToAck();

      //try {sleep(10000L);} catch (InterruptedException e) {}
    }   // While True on opening the socket
    hbHandler.heartBeatIn();
    instate=2;
    long lastThrottleCalc = System.currentTimeMillis();
    lastThrottle = lastThrottleCalc;
    throttleBytes=0;
    lastThrottleAvg=lastThrottle;
    totalThrottleBytes=0;
  }
  private void resetTailToAck() {
    if(queue == null) return;
    if(lastAckSeq == -1) return;
    int tail = queue.getTailSeq() % seqLimit;
    int places = tail - lastAckSeq;
    if(places < 0) places += seqLimit;
    par.prta(tag+" *** do a seq rollback lastSeq="+lastAckSeq+" tailseq="+tail+" places="+places+
            " ok="+(places > 0?queue.moveTailBack(places):"zero"));
  }
  /** set a new socket to receive from.  This is only called when EXPORT is in server (passive) mode
   *
   * @param ss The socket to use going forward
   * @param dbgdataExport A string used to set debug mode on one export
   */
  public void newSocket(Socket ss, String dbgdataExport) {
    if(s != null) {
      if(!s.isClosed()) try{s.close();ndisconnect++;} catch(IOException e) {}
    }
    //try{sleep(1000);} catch(InterruptedException e) {}
    dbgdata=false;
    if(dbgdataExport.equals(exportHost.trim())) {dbgdata=true;dbg=true;}
    par.prt(tag+" "+" New connection set to "+ss+" dbgdata="+dbgdata+" "+dbgdataExport+
            " exportHost="+exportHost+"| alive="+isAlive()+" state="+state+"/"+instate+" BH alive="+hbHandler.isAlive());
    
    s = ss;
    if(lastAckSeq != -1 && queue != null) resetTailToAck();
    long lastThrottleCalc = System.currentTimeMillis();
    lastThrottle = lastThrottleCalc;
    throttleBytes=0;
    lastThrottleAvg=lastThrottle;
    totalThrottleBytes=0;

  }
  /**
   * This class handles the heartbeats.  Since this is a EXPORTER it does all of the reading
   * from the socket, as the only input expected are heartbeats and ACKs.
   *
   */
  class EWHeartBeatHandler extends Thread {
    byte [] hb;
    int interval;
    int rcvinterval;
    long lastHeartBeatIn;
    int nhbTimeouts;
    boolean terminate;
    byte [] hbMsgInBytes;
    public void terminate() {terminate=true; this.interrupt();par.prt("EWHeartBeatHandler.terminate() has been called");}
    public final void heartBeatIn() {
      lastHeartBeatIn = System.currentTimeMillis(); 
      if(dbg) 
        par.prta(tag+"HB in ");
    }
    public int getHeartBeatTimeOuts() {return nhbTimeouts;}
    public EWHeartBeatHandler(byte inst, byte module, String msg, int intersec, int recintsec) {
      hb = new byte[10+msg.length()+1];
      hb[0]=STX;
      hb[10+msg.length()] = ETX;
      String s2 = Util.df3(inst)+Util.df3(module)+Util.df3(EWMessage.TYPE_HEARTBEAT);
      System.arraycopy(s2.getBytes(), 0, hb, 1, 9);
      System.arraycopy(msg.getBytes(), 0, hb, 10, msg.length());
      interval=intersec;
      rcvinterval=recintsec;
      hbMsgInBytes = hbMsgIn.getBytes();
      heartBeatIn();
      start();
    }
    @Override
    public void run() {
      long curr;
      long lastWrite = System.currentTimeMillis();
      boolean escaping=false;
      int pnt=0;
      int state=EXPECTING_START;
      byte [] b = new byte[100];
      byte [] buf = new byte[100];
      lastHeartBeatIn = System.currentTimeMillis(); 
      
      while(!terminate) {
        try {sleep(10);} catch(InterruptedException e) {}
        if(s != null) {     // if we have a connection
          try {
            if(!s.isClosed()) {
              curr = System.currentTimeMillis();
              if(curr - lastWrite > interval*1000) {
                if(dbg) par.prta(tag+" * Write HeartBeat|"+Util.toAllPrintable(new String(hb))+"|");
                synchronized(mutex) {
                  s.getOutputStream().write(hb);
                }
                bytesOut += hb.length;
                lastWrite=curr;
              }


              while(s.getInputStream().available() > 0) {
                // Get the header
                int l = s.getInputStream().read( b, 0, Math.min(s.getInputStream().available(), b.length));
                if(l == 0) break;       // EOF found skip
                bytesIn += l;
                instate=6;
                for(int i=0; i<l; i++) {
                  instate=100+state;
                  switch (state) {
                   case IN_MESSAGE:
                      if(b[i] == ESC) {
                        if(escaping) {        // escaping an escape!
                          buf[pnt++] = b[i];
                          escaping=false;
                        }
                        else escaping=true;
                      }
                      else {                  // Its not an escaped character
                        if(pnt >= buf.length) {
                          par.prt(tag+"* Got IMPORT buf longer than length double buf size. pnt="+pnt+" buf.len="+buf.length);
                          byte [] tmp = new byte[buf.length*2];
                          System.arraycopy(buf, 0, tmp, 0, buf.length);
                          buf = tmp;
                        }
                        buf[pnt++] = b[i];

                        if(b[i] == ETX && !escaping) {
                          state=EXPECTING_START;
                          instate=8;
                          // If this is a ACKing link, send back the ACK

                          if(buf[10] == 'A' && buf[11] == 'C' && buf[12] == 'K' && buf[13] == ':') {
                            // process the received ACK
                            if(dbg) 
                              par.prta("Got ack "+(char)buf[14]+(char) buf[15]+(char)buf[16]);
                            lastAckSeq = (buf[14]-'0')*100+(buf[15]-'0')*10+(buf[16] - '0');
                          }
                          else {
                            boolean ok =true;
                            for(int j=0; j<hbMsgInBytes.length; j++) if(hbMsgInBytes[j] != buf[j+10]) ok=false;
                            if(ok) heartBeatIn();
                            else  {
                              instate=13; 
                              par.prt(tag+"Hb should be "+hbMsgIn+" * Unhandled message type="+buf[EWMessage.LOGO_TYPE_OFFSET]+" "+
                                    buf[0]+" "+buf[1]+" "+buf[2]+" "+buf[3]+" "+buf[4]+" "+buf[5]+" "+buf[6]+" "+
                                    buf[7]+" "+buf[8]+" "+buf[9]+"|"+buf[10]+" "+
                                    buf[11]+" "+buf[12]+" "+buf[13]+" "+buf[14]+" "+buf[15]+" "+
                                    buf[16]+" "+buf[17]+" "+buf[18]+" "+buf[19]+" "+buf[20]+" "+buf[21]+
                                      " last="+buf[pnt-1]+" "+Util.toAllPrintable(new String(buf, 0, pnt-1)));
                            }
                          }
                          npackets++;
                          for(int j=0; j<100; j++) buf[j] = 0;
                        }
                        escaping=false;
                      }   // else - its a legitimite keeper character
                      break;
                    case SEARCHING_START:
                      if(b[i]  == STX) {
                        if(!escaping) {
                          state = EXPECTING_START;
                          pnt=0;
                          par.prt("Skipped "+skipped+" to get resynced");
                        }
                      }
                      else {
                        skipped++;
                        break;
                      }
                    case EXPECTING_START:
                      if(b[i] == STX) {   // GOt it, if not bad news
                        pnt=0;
                        buf[pnt++] = b[i];
                        state = IN_MESSAGE;
                      }
                      else {
                        nsyncError++;
                        par.prt(tag+"**** lost sync.  STX not where expected #="+nsyncError);
                        state=SEARCHING_START;
                        pnt=0;
                      }
                      break;
                    default:
                      par.prt(tag+"*** Default - this should never happen");
                  }
                }
              }

              // Check for missing hearbeats
              if(curr - lastHeartBeatIn > rcvinterval*1000) {      // has it been too long for inbound heartbeats
                par.prta(tag+"** No heartbeats from IMPORT end in "+((System.currentTimeMillis() - lastHeartBeatIn)/1000)+" secs");
                nhbTimeouts++;
                lastHeartBeatIn = curr;   // let it time out again.
                try {
                  if(!s.isClosed()) {
                    s.close();
                    ndisconnect++;
                    s=null;
                  }
                }
                catch(IOException e) {
                  par.prt(tag+"IOError closing socket on heartbeat time out e="+e);
                }
              }            
            }
          }
          catch(IOException e) {
            par.prta(tag+" EW Heartbeat error.  Close socket. e="+e);
            e.printStackTrace(par.getPrintStream());
            if(!s.isClosed()) try{s.close(); s= null; ndisconnect++;} catch(IOException e2) {}
          }
          catch(RuntimeException e) {
            if(s == null) continue;   // probably the socket being closed
            e.printStackTrace(par.getPrintStream());
            par.prta(tag+" Runtime error!");
          }
        }
      }
      par.prta(tag+"EWHeartBeatHandler is exiting. terminate="+terminate);
    }

  }

}
