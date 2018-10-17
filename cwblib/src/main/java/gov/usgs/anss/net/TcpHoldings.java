/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * TcpHoldings.java
 *
 * Created on May 9, 2005, 4:16 PM
 */

package gov.usgs.anss.net;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.edgethread.MonitorServer;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/** This class is the server which accepts TCP/IP connections from an HoldingSender
 * and updates the holdings table in the status database with the connects so communicated.
 * Usually it is run from a cron using the chkJarProcess?? bash script.*
 * 
 *<p>
 *The edge.prop file is read by the EdgeProperties class and the MySQLServer tag is used to 
 * identify the correct database server for this installation.  This is the normal way of setting
 * they database environment.  There is a debugging command line option to override this server.
 *
 *<PRE>
 *Switch  arg      Description
 * -p     pppp     Port number if default 7996 is not to be used
 * -h     ip.ad.dd The IP address of the database server (overrides edge.prop DBServer property)
 *</PRE>
 *
 * @author davidketchum
 */
public class TcpHoldings extends EdgeThread {
  private ServerSocket ss;
  private final List clients   = Collections.synchronizedList(new LinkedList());
  private GregorianCalendar lastStatus;
  private int port;
  private DBConnectionThread  db;
  private MemoryChecker memchk;
  @Override
  public void terminate() {
    terminate=true;
    memchk.terminate();
    if(!ss.isClosed()) { 
      try {
        ss.close();
      }
      catch(IOException e) {}
    }
  }
  /** return the monitor string for ICINGA
   *@return A String representing the monitor key value pairs for this EdgeThread */
  @Override
  public StringBuilder getMonitorString() {
    if(monitorsb.length() > 0) monitorsb.delete(0, monitorsb.length());
    monitorsb.append(MemoryChecker.getMonitorString());
    monitorsb.append("TCPHSenders=").append(clients.size()).append("\n");
    return monitorsb;    
  }
  @Override
  public StringBuilder getStatusString() {return statussb;}
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
  /** Creates a new instance of TCPHoldings
    * @param argline The argument line for this EdgeThread
    * @param tg The logging tag for this edgeThread
   */
  public TcpHoldings(String argline, String tg) {
    super(argline,tg);
    String [] args = argline.split("\\s");
    port=7996;
    int monOffset=0;
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-p")) port = Integer.parseInt(args[i+1]);
      if(args[i].equals("-h")) {Util.setProperty("StatusDBS Server", args[i+1]);}
      //if(args[i].equals("-instance")) {monOffset = Integer.parseInt(args[i+1]);}
    }
    prta("args len="+args.length+" StatusDBServer="+Util.getProperty("StatusDBServer")+" port="+port);
    db = DBConnectionThread.getThread("TCPHstatus");
    while(db == null) {
      try {
        db = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status", true,false,"TCPHstatus", getPrintStream());
        addLog(db);
        if(!DBConnectionThread.waitForConnection("TCPHstatus"))
          if(!DBConnectionThread.waitForConnection("TPCHStatus"))
            if(!DBConnectionThread.waitForConnection("TPCHStatus"))
              if(!DBConnectionThread.waitForConnection("TPCHStatus"))
                if(!DBConnectionThread.waitForConnection("TPCHStatus"))
                {
                  Util.prta("**** Did not promptly connect to status!");
                  SendEvent.edgeSMEEvent("TcpHldBadDB", "Cannot connect to "+Util.getProperty("StatusDBServer"), "TcpHoldings");
                  Util.sleep(30000);
                }
      }
      catch(InstantiationException e ) {
        Util.prt("Instantiation error on status db impossible");
        db = DBConnectionThread.getThread("TCPHstatus");
      }
    }
    Holding.setHoldingsConnection(db);     // Set this as the connection to use
    Runtime.getRuntime().addShutdownHook(new ShutdownTcpHoldings());
    try {
      ss = new ServerSocket(port);
      lastStatus = new GregorianCalendar();
      prt("TCPHoldings: listening on port="+port);
      memchk = new MemoryChecker(600, this);
      MonitorServer monitor = new MonitorServer(monOffset < 100?AnssPorts.MONITOR_TCPHOLDING_PORT +monOffset:monOffset, this);
      start();
      // The DB and table are in the first two, insure we have a connection open to that DB

    }
    catch(IOException e) {
      prt("TCPHoldings: Cannot set up socket server on port "+port);
      e.printStackTrace();
      Util.exit(10);
    }
  }
  @Override
  public void run() {
    SendEvent.edgeSMEEvent("TcpHoldStart", "TcpHoldings is starting up node="+Util.getNode(), "TcpHoldings");
    prt(Util.asctime()+" TCPHoldings : start accept loop.  I am "+
      ss.getInetAddress()+"-"+ss.getLocalSocketAddress()+"|"+ss);
    while (true) {
      prta(" TCPHoldings: call accept()");
      if(terminate) break;
      try {
        Socket s = ss.accept();
        s.setReceiveBufferSize(60000);
        if(terminate) break;
        boolean changed=true;
        synchronized (clients) {
          while(changed) {
            Iterator it = clients.iterator();
            changed=false;
            while (it.hasNext()) {
              Client cc = (Client) it.next();

              if(cc.getIpadr().equals("Closed.ip.adr") || cc.isClosed()) {
                prta(cc.toString()+" TCPHoldings: Client socket has been closed.");
                clients.remove(cc);
                changed=true;
                break;
              }
            }
          }
          prta(Util.ascdate()+" TCPHoldings: new socket="+s+" at client="+clients.size());
          Client c = new Client(s);
          clients.add(c); 
          
        }
      }
      catch (IOException e) {
        if(!ss.isClosed()) {        // this is because we are shuting down.
          prta("TCPHoldings: accept gave IOException!");
          e.printStackTrace();
        }
        terminate=true;
      }
    }
    prta("TCPHoldings accept thread is terminated.");
    System.exit(99);
  }  
  
  /** this client receives data from one socket connection and feeds it to a
   * process server.
   */
  private class Client extends Thread {
    Socket s;
    String socketName;
    String tag;
    OutputStream out;
    InputStream in;
    byte [] buf;
    int totmsgs;
    ProcessHoldings processor;
    int msgLength;              // msg length per the processor
      HoldingArray ha;
    DebugTimer timer;
    int QUEUE_SIZE;
    boolean isClosed;
    boolean isRunning;
    boolean clientTerm;
    long lastRead;
    public String getTag() {return socketName;}
    @Override
    public String toString() {return socketName+" closed="+isClosed+" run="+isRunning+" term="+clientTerm;}
    public boolean isClosed(){return isClosed;}
    public void terminate() {
      clientTerm=true; 
      if(s != null) try{if(!s.isClosed()) s.close();} catch(IOException e) {}
    }

    public Client(Socket st) {
      QUEUE_SIZE=100;
      clientTerm=false;
      s = st;
      /*int end = s.toString().indexOf(",local");
      int beg = 13;
      if(end < 0) {
        end=s.toString().length()-1; 
        beg=0;
      }*/
      socketName="["+s.getInetAddress().toString().substring(1)+"/"+s.getPort()+" "+Util.now().toString().substring(5, 16)+
          "-"+getName().substring(7)+"]";
      tag="THUC:"+socketName;
      ha = new HoldingArray();
      ha.setTag(socketName);
      processor=new ProcessHoldings(QUEUE_SIZE, ha);
      processor.setTag(socketName);
      timer = new DebugTimer(this);
      msgLength=processor.getMsgLength();
      buf = new byte[msgLength];
      try {
        out = s.getOutputStream();
        in = s.getInputStream();
      } catch(IOException e) {
        prt("TCPHoldings: Create client failed to get OutputStream");
        e.printStackTrace();
      }
      start();
    }
    
    /**
     * For each "client" we read in and IP address and command
     */
    @Override
    @SuppressWarnings("empty-statement")
    public void run() {
      isRunning=true;
      boolean dbg=false;
      int l;
      int len=0;
      PrintStream lst=new PrintStream(out);     // ANy console output goes back on this socket
      while(!clientTerm) {
        try
        { //prta("CLient start read"+getIpadr());
          while(processor.getNused() > (QUEUE_SIZE-3)) {  // if near full, wait until some process
            try { sleep(10);} catch (InterruptedException e) {}
          }
          l=0;
          if(terminate) break;
          
          while(in.available() < msgLength) {Util.sleep(10); if(clientTerm) break;}
          if(clientTerm || terminate) break;
          while (l < msgLength) {
            len = in.read(buf, l, msgLength-l);
            if(len == -1) { // The EOF condition close up
              break;
            }
            l += len;
          }
          lastRead=System.currentTimeMillis();
          if(clientTerm) break;
          //prt("read len="+len);
          if(len == -1) {prta(tag+" EOF reading from "+toString());break;}
          
          if(msgLength != l) prt(tag+" Short input len="+len+" "+toString());
          processor.queue(buf);
          totmsgs++;
          
          // Throttle our processing slightly to give others a chance.
          if(totmsgs % 100 == 0) try {sleep(10);} catch(InterruptedException e) {};
          if(totmsgs % 100000 == 0) prta(socketName+" Holding stats tot="+totmsgs+" "+Holding.getHoldingStats());

        }
        catch (IOException e)
        {
          try
          { if(e.getMessage().equals("Socket closed") || e.getMessage().contains("Stream closed")) 
              prta(tag+" closed socket.  Close up");
            else Util.IOErrorPrint(e, tag+" THUC : read socket failed");
            if(s != null) s.close();
            s=null;
            break;
          }
          catch(IOException e2)
          {
            Util.IOErrorPrint(e2,tag+" ClientSocket: ClientSocket IOException while closing bad socket");
            break;
          }
        }
      }         // while(true) run() forever
      prta(tag+"TcpHoldings client: run() exiting. term="+terminate+" len="+len);
      isRunning=false;
      try {
        close();
      }
      catch(IOException e) {
        prta("Error when closing data structures");
      }
    }
    public String getIpadr() {
      if(s == null) return "Closed.ip.adr";
      return socketName;
    }
    
    public synchronized void close() throws IOException {
      // prevent multiple closings
      if(isClosed) {prta(toString()+" Multiple close detected and aborted!!!!"); return;}
      int loops=0;
      prta(tag+" closing.  Wait for no more input.");
      while(System.currentTimeMillis() - lastRead < 5000 && loops < 450) {
        try{sleep(100);} catch(InterruptedException e) {}
        loops++;
      }
      prta(tag+" closing.  processor terminate and wait for done. elapsed="+(loops+5)/10+" secs");
      loops=0;
      clientTerm=true;
      processor.setTerminate();
       while(processor.getNused() != 0) {
        if(loops % 50 == 0) prta(toString()+" Close: wind down input queue="+processor.getNused()+" "+
            loops+" in="+processor.getNextin()+
            " out="+processor.getNextout()+" state ha="+ha.getState()+" proc="+processor.getStatertn());
        try{ sleep(100);} catch (InterruptedException e) {}
        if(loops++ > 200) break;     // timeout on break
      }
      //prta("Close: consolidate start "+toString());
      //int combine=ha.consolidate(100000);
      prta(toString()+"Close: purge start elapsed="+(loops+5)/10+" secs");
      terminate();
      
      int purge=ha.purgeOld(-1);
      ha.close();               // release any memory this ha has
      prta(toString()+" Close Client "+//" combine="+combine+
              " purge="+purge+" nhold="+ha.getSize());
      if(s != null) s.close();
      s=null;
      isClosed=true;
    }
    public void send(byte [] buf, int len) throws IOException {
      try {
        out.write(buf, 0, len);
      }
      catch (IOException e) {
        try {
          s.close();
          throw e;
        }
        catch(IOException e2) {
          prt(toString()+"TCPHoldings: Client IOException while closing bad socket");
          throw e2;
        }
      }
    }
    private class DebugTimer extends Thread {
      Client cc;
     public DebugTimer(Client c) {
       cc = c;
       start();
     }
      @Override
     public void run() {
       int purge=0;
       int combine=0;
       int timeout=0;
       int loop=0;
       int noactivity=0;
       long top=System.currentTimeMillis();
       long tmp;
       long tpurge=0;
       long tcomb=0;
       long tkeep=0;
       int lastTotmsgs=totmsgs;
       int consolidateMod=30;
       this.setPriority(Thread.MIN_PRIORITY);
       while (true) {
         if(terminate) break;
         try { 
           sleep(Math.max(10,1000L-(System.currentTimeMillis()-top)));       // one second loop
           top=System.currentTimeMillis();
            //if( (loop % 10) == 0) timeout += ha.timeoutWrite(60000,100); // Write out any that are at least 10 seconds old
           if( (loop % 1800) == 0) {
             tmp=System.currentTimeMillis();
             purge += ha.purgeOld(7200);       // Purge to every couple hours
             tpurge += System.currentTimeMillis()-tmp;
           }
           if( (loop % consolidateMod) == 10) {
             tmp=System.currentTimeMillis();
             int ncomb=ha.consolidate(200);    // consolidate
             if(ncomb < 200) consolidateMod *=2;
             else consolidateMod = 60;
             if(consolidateMod > 1800) consolidateMod=1800;
             combine += ncomb;
             tcomb += System.currentTimeMillis()-tmp;
             prta(cc.getTag()+" cons ncomb="+ncomb+" mod="+consolidateMod);
           }
           //if( (loop % 30) == 5) {
             tmp=System.currentTimeMillis();
             timeout += ha.keepWriting(300,10);   // always update a few, if every 30 seconds this was 200
             tkeep += System.currentTimeMillis() - tmp;
           //}
           //if( (loop % 300) == 0) ha.list();
           if(loop % 300 == 0) {
             prta(Util.ascdate().substring(5)+" "+cc.getTag()+" tot="+totmsgs+
                " hi="+processor.getHighwater()+ 
                " q="+processor.getNused()+" nh="+ha.getSize()+
                " Cmb="+combine+" upd="+timeout+" pur="+purge+
                " tpur="+(tpurge/1000)+" tcmb="+(tcomb/1000)+" tkp="+(tkeep/1000)+" md="+consolidateMod);
              combine=0;
              timeout=0;
              purge=0;
              if(totmsgs == lastTotmsgs) {
                noactivity++;
                if(noactivity > 4) {
                  prta(cc.toString()+"No activity time out : "+cc.toString());
                  try {
                    cc.close();
                  }
                  catch(IOException e) {
                    prt("IOException closing timed out unit. Break out");
                    break;
                  }
                }
                
              }
              else {lastTotmsgs=totmsgs; noactivity=0;}
              if(getIpadr().equals("Closed.ip.adr")) {
                prta(cc.toString()+"Client closed.  Final debugTimer report ");
                 break;   // exit this
              }
           }
           loop++;
           //ha.list();
         }
         catch (InterruptedException e) {
           prt(cc.toString()+"DebugTimer interupted");
         }
       }
       prta(cc.toString()+"DebugTimer terminated ");
     }
    }       // End of DebugTimer 
    
  } // end of class Client

  private class ShutdownTcpHoldings extends Thread {
    public ShutdownTcpHoldings() {
      
    }
    
    /** this is called by the Runtime.shutdown during the shutdown sequence
     *cause all cleanup actions to occur.
     */
    
    @Override
    public void run() {
      terminate();
      prta("ShutdownTcpHoldings started");
      SendEvent.edgeSMEEvent("TcpHoldSDwn", "TcpHoldings is shutting down on node="+Util.getNode(), "TcpHoldings");
      //SimpleSMTPThread.email(Util.getProperty("emailTo"), "TcpHoldings is shutting down node="+Util.getNode(),
      //    "This comes from TcpHoldings when the ShutdownHandler is executed.  Node="+Util.getNode());
      try{sleep(15000);} catch(InterruptedException e) {}// give some time for other processes to empty their holdings
      if(!ss.isClosed()) {
        try{
          ss.close();
        }
        catch (IOException e) {}
      }
      int i=0;
      synchronized (clients) {
        Iterator it = clients.iterator();
        while(it.hasNext()) {
          Client cc = (Client) it.next();
          prta("ShutdownTcpHoldings: close "+i+" client="+cc.toString());
          i++;
          try {
            cc.close();
          }   // This starts the termination - need to wait for all to exit
          catch(IOException e) {
            prta(tag+" shutdown holdiner io error e="+e+" on "+cc);
          }
        }
      }
      boolean done=false;
      int loop=0;
      while(!done) {
        try{sleep(2000);} catch(InterruptedException e) {}
        Iterator<Client> it = clients.iterator();
        done = true;
        while(it.hasNext()) {
          Client c = it.next();
          if(!c.isClosed()) {
            prta("CLient "+c+" is not closed!");
            done = false;
          }
        }
        if(loop++ > 100) done=true;
      }
      prta(Util.ascdate()+" Shutdown holdings completed.");
      //DBConnectionThread.shutdown();
      for( i=0; i<3; i++) {
        Util.showShutdownThreads("TCPHoldings is it shutdown? "+i);
        try{sleep(5000);} catch(InterruptedException e) {}
      }
      
    }
  }
  
   /**
  * @param args the command line arguments
  */

  public static void main(String[] args) {
    Util.setProcess("TcpHoldings");
    Util.setModeGMT();
    Util.setNoconsole(false);      // No user dialogs if errors foundrm
    Util.setNoInteractive(true);
    EdgeProperties.init();
    EdgeThread.setMainLogname("tcpholdings");   // This sets the default log name in edge thread (def=edgemom!)
  
    String argline="";
    for(int i=0; i<args.length; i++) argline+=args[i]+" ";
    TcpHoldings t = new TcpHoldings(argline, "TCPH");

  }  
   
}
