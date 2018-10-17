/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.net;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

/** This class creates a socket to a SeisNetWatch SocketAgent for sending messages of the form
 * NN-SSSSS:Key1=Value1;Key2=Value2;... KeyN=ValueN\n
 * On creation it sets up the end point and stores the target
 * IP and port.
 * 
 * This will be disabled if the SNWHost property is empty or the SNWPort is zero.  The defaults
 * for the host is gsnw and the port is 10008 which is correct for the NEIC only.
 *
 * @author davidketchum
 */
public final class SNWSender extends Thread {
  private int MAX_LENGTH;
  private String host;
  private int port;
  private byte [] buf;
  private byte [] type;
  private static boolean disabled;      // if set, make all SNWSenders mute

  // These are eneeded if TCP/IP is used
  private Socket ss;          // The TCP/IP socket (if used) 
  private OutputStream out;
  private InputStream in;
  private ArrayList<byte[]> bufs;
  private int [] lengths;
  private int bufsize;
  private int nextin;
  private int nextout;
  private int ndiscard;
  private long totalrecs;
  private int maxused;
  private int maxLengthSeen;
  private SNWSenderShutdown shutdown;
  private StringBuilder tag = new StringBuilder(10);
  
  // status and debug
  long lastStatus;
  boolean terminate;
  boolean dbg;
  public int getUsed() { int used=nextin -nextout; if(used < 0) used += bufsize; return used;}
  public static void setDisabled(boolean t) {disabled=t;}
  /** Creates a new instance of SNWSender 
   * @param maxQueue Number of messages to reserve space for in queue
   * @param maxLength Maximum record length for these messages
   * @throws java.net.UnknownHostException
   */
  public SNWSender( int maxQueue, int maxLength) throws UnknownHostException {
    bufsize=maxQueue;
    host="localhost";
    if(Util.getProperty("SNWHost") != null) host = Util.getProperty("SNWHost");
    if(Util.getProperty("SNWServer") != null) host = Util.getProperty("SNWServer");
    if(Util.getProperty("SNWPort") != null) port = Integer.parseInt(Util.getProperty("SNWPort"));
    else port = 10008;
    if(host.equals("")) disabled=true;
    MAX_LENGTH = maxLength;
    buf = new byte[MAX_LENGTH];
    bufs = new ArrayList<>(bufsize);
    nextin=0;
    terminate=false;
    nextout=0;
    lengths = new int[bufsize];
    try {
      shutdown = new SNWSenderShutdown();
      Runtime.getRuntime().addShutdownHook(shutdown);
    }
    catch(IllegalStateException e) {
      // if this happens, this creation is happening during a shutdown, just bale!
      Util.prta("SNWS: Attempt to start a SNWSender during a shutdown?  got an IllegalState error");
      return;
    }
    for(int i=0; i<bufsize; i++) {
      bufs.add(i, new byte[MAX_LENGTH]);
    }
    this.setDaemon(true);
    if(port == 0) disabled=true;
    tag.append(tag).append("[0]");
    Util.prta("SNWS: new SNWSender start : host="+host+"/"+port+" disabled="+disabled);
    start();
  }
  
  public String getStatusString() {return host+"/"+port+" nblks="+totalrecs+" discards="+ndiscard+
      " in="+nextin+" out="+nextout+" qsize="+bufsize;}
  @Override
  public String toString() {return getStatusString();}
  public void terminate() {
    terminate=true;
    if(ss == null) return;
    try {
      if(!ss.isClosed()) ss.close();
    }
    catch(IOException e) {}
  }
  public synchronized boolean queue(String s) {
    if(s.contains(" -") && s.indexOf(" -") <=5) {    // This is the one character net code!
      s=s.replaceFirst(" -", "_-");
    }
    if(s.trim().charAt(s.trim().length()-1) == '\n') 
      return queue(s.trim().getBytes());
    return queue((s.trim()+"\n").getBytes());
  }
  public synchronized boolean queue(StringBuilder s) {
    if(dbg || s.length() >= buf.length) 
      Util.prta(tag+"SNWS: queue(SB) : nin="+nextin+" nout="+nextout+" len="+s.length()+" "+s);
    int len=0;
    if(s.charAt(1) == ' ') s.delete(1,2).insert(1, '_');
    for(int i=0; i<s.length(); i++)  {buf[len++] = (byte) s.charAt(i);}
    return queue(buf, 0, len);
  }
  public synchronized boolean queue(byte [] bf) { 
    new RuntimeException(tag+"SNWS: send by queue(buf) - this should be obsolete! "+new String(bf)).printStackTrace();
    return queue(buf, 0, bf.length);
  }
  public synchronized boolean queue(byte [] bf, int offset, int len) {
    if(disabled) return true;
    if(len > maxLengthSeen) maxLengthSeen = len;
    if(len > MAX_LENGTH) {
      new RuntimeException(tag+"SNWS: called with buffer to big. Skip. len="+len+" max="+MAX_LENGTH+" "+new String(bf, offset, len)).printStackTrace();
      return false;
    }
    if(bf[offset] == 0 && bf[offset+1] == 0 || bf[offset+2] == 0) {
      Util.prta(tag+"SNWS: called with zeros in buffer - skip");
      new RuntimeException(tag+"SNWS: called with zeros in buffer").printStackTrace();
      return false;
    }
    int next = nextin+1;
    if(next >= bufsize) next=0;
    if(next == nextout) {
      if(ndiscard % 1000 == 0) Util.prta("SNWS: discarding SNW msg - queue is full  next="+next+" nout="+nextout+" ndiscard="+ndiscard+" size="+bufsize);
      ndiscard++;
      return false;
    }
    try {
      System.arraycopy(bf, offset, bufs.get(nextin), 0, len);
    } 
    catch(ArrayIndexOutOfBoundsException e) {// This is to look for a bug!
      Util.prta(tag+"SNWS: OOB nextin="+nextin+
          " len="+bf.length+" "+((byte []) bufs.get(nextin)).length+" "+len+
          " buf="+new String(bf)+" "+new String(bufs.get(nextin)));
      Util.prta(tag+"SNWS: arraycopy OOB exception e="+e.getMessage());
      e.printStackTrace();
      Util.exit(0);
    }
    lengths[nextin] = len;
    nextin++;
    if(nextin >= bufsize) nextin=0;
    if(getUsed() > maxused) maxused=getUsed();
    return true;
  }
  /**write packets from queue*/
  @Override
  public void run() {
    boolean connected;
    out = null;
    StringBuilder st = new StringBuilder(10);
    byte [] newline = "\n".getBytes();
    int err=0;
    byte [] okays = new byte[3];
    // In UDP case data is sent by the send() methods, we do nothing until exit

    
    // Create a timeout thread to terminate this guy if it quits getting data
    while(!terminate) {
      connected = false;
      // This loop establishes a socket
      while( !terminate) {
        if(disabled) {
          try{sleep(10000);} catch(InterruptedException e) {}
          continue;
        }
        try { 
          ss = new Socket(host, port);
          Util.clear(tag).append("SNWS:[").append(ss.getLocalPort()).append("]");
          Util.prta(tag+"SNWS: Created new socket to SocketAgent at "+host+"/"+
              port+" dbg="+dbg+" qsize="+bufsize);
          if(terminate) break; 
          out = ss.getOutputStream();
          in = ss.getInputStream();
          connected=true;
          break;
        }
        catch(UnknownHostException e) {
          Util.prta(tag+"SNWS: Unknown host for socket="+host+"/"+port);
          try {sleep(300000);} catch(InterruptedException e2) {}
        }
        catch(IOException e) {
          if(e.toString().contains("Connection refused")) {
            Util.prta(tag+"SNWS: Connection refused by "+host+"/"+port+" Server down wait 30 and try again.");
          }
          else Util.SocketIOErrorPrint(e,tag+" connecting to "+host+"/"+port);
          try {sleep(30000);} catch(InterruptedException e2) {}
        } 
      }

      // WRite out data to the TcpHoldings server
      lastStatus = System.currentTimeMillis();
      int nblks=0;
      int nused;
      while(!terminate) {
        try {
          // read until the full length is in 
          int l=0;
          while(nextin != nextout) {
            Util.clear(st);
            for(int i=0; i<lengths[nextout]; i++) st.append((char) bufs.get(nextout)[i]);
            //String st = new String((byte []) bufs.get(nextout));
            //if(st.indexOf("US-HAWA") >=0) Util.prt(tag+""+st);
            if(dbg)
              Util.prta(tag+"SNWS: detail  nin="+nextin+" nout="+nextout+" len="+lengths[nextout]+" "+st+"|");
            
            out.write((byte []) bufs.get(nextout), 0, lengths[nextout]);
            if(bufs.get(nextout)[lengths[nextout]-1] != '\n') out.write(newline);
            nblks++;
            totalrecs++;
            //if(nblks % 100 == 0) Util.prta("Done block="+nblks);
            nextout++;
            if(nextout >= bufsize) nextout = 0;
            // put the block in the queue
            if(terminate) break;
            l=in.read(okays,0, 3);
            if(l == -1) {
              Util.prta(tag+"SNWS: EOF on read - close socket");
              ss.close();
              break;
            }
            if(l == 3 && okays[0]==79 && okays[1] == 75 && okays[2] == 10){
              if(dbg) Util.prta(tag+"SNWS: OK rcv on st="+st);
            }
            else  
              Util.prta(tag+"SNWS: 1="+l+"okays="+okays[0]+" 2="+okays[1]+" 3="+okays[2]+" on "+st);
          }
          if(l == -1) break;
          if( System.currentTimeMillis() - lastStatus > 300000) {
            Util.prta(tag+"SNWS: via TCP nblks="+nblks+" nxtin="+nextin+" nxtout="+nextout+
                " used="+getUsed()+" maxused="+maxused+" maxLenSeen="+maxLengthSeen+"/"+MAX_LENGTH+" discards="+ndiscard);
            lastStatus = System.currentTimeMillis();
            nblks=0;
          }
          try{sleep(100);} catch(InterruptedException e) {}
        }
        catch(IOException e) {
          Util.SocketIOErrorPrint(e,tag+"SNWS: writing to socket");
          Util.prta(tag+"SNWS: IO error close and reconnect e="+e);
          if(!ss.isClosed()) {
            try {
              ss.close();
            }
            catch(IOException e2) {}
          }
        }
        //Util.prta(tag+"timer");
        if(ss.isClosed()) break;       // if its closed, open it again
      }             // end of while(!terminate) on writing data

      // IF the eto went off and set terminate, do not terminate but force the socket closed
      // and go back to reopen loop.  This insures that full exits only occur when the program
      // is shutting down.
      try{sleep(10000L);} catch (InterruptedException e) {}
    }               // Outside loop that opens a socket 
    Util.prta(tag+" ** SNWSender terminated ");
    if(ss != null)
      if(!ss.isClosed()) {       // close our socket with predjudice (no mercy!)
        try {
          ss.close();
        }
        catch(IOException e2) {}
      }
    Util.prta(tag+"  ** exiting");
  }
  class SNWSenderShutdown extends Thread {
    public SNWSenderShutdown() {
    }
    
    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println(tag+" Shutdown() started...");
      terminate();
    }
  }
}
