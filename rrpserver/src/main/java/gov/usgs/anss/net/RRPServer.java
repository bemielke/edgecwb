/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * RPPServer.java 
 *
 * Created on May 2, 2007, 4:55 PM
 *
 * To change g this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.net;

import gov.usgs.anss.RRPUtil.Util;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

/** This class runs the "remote" end of an RRP client server.  This software offers
 * connections to a port, accepts one-and-only-one connection from each of a list of IP addresses
 * and writes out the ring file.  For most uses the list will only contain one IP address since
 * the most likely use is one connection from Golden. 
 * The  list of IP addresses for all clients must be set with the -ip
 * switch so that stray connections or connections from scans do not interrupt the connection.
 * This ring is a replication of the ring at the RRPClient.  The
 * class insures all data are received correctly and written to the file and that the 
 * file pointer is consistently updated when new data are available.
 * <br>
 * Configuration of multiple connections can be done on the command line where the -ip, -wait, -s, and -f are
 * a colon separated list for each connection in corresponding order.  Example :
 * <br>
 * -ip 1.2.3.4:4.5.6.7 -wait 1000:100 -s 200:1000 -f RING1.ring:RING2.ring
 * <p>
 * or multiple connections can be setup by having these 4 switches on separate lines in a 
 * configuration file specified with the -config switch.  So if there is a config file "rrps.setup" containing :
 * <br>
 * <PRE>
 * -ip 1.2.3.4 -wait 1000 -s 200 -f RING1.ring
 * -ip 4.5.6.7 -wait 100 -s 200 -f RING2.ring
 * </PRE>
 * <br>
 * would accomplish the same configuration as the above command line.  All other paramters apply to 
 * the entire RRPServer process regardless of which method is used to configure the allowed connections.
 *<br>
 * Typical command :  java -jar RRPServer.jar -ip 1.2.3.4 -f NEIC.ring -s 200 -u 500 -log logfile.log
 *<br><br>
 *Command line arguments :
 *<br> .0
 * <PRE>
 * switch arg                 Description
 * -p nnnn                   Set port number (def=22223)
 * -ip ip.dot.add:ipadr2:... Set the IP addresses from which connections will be accepted.
 * -s nn:nn2:...             Set the file size to nnnn MB (must divide evenly into 1,000,000"
 * -wait  ms1:ms2:....       Set ms to wait for each 256 blocks (1000 = 1 mb/s and is the default)
 * -u ms                     Set time interval between updates of ring metadata file to ms milliseconds (def=1000)
 * -up mod                   Set packets interval between updates of ring meta data file to every 'mod' packets (def=never)
 * -a  nnnn                  Set number of millseconds between acks to client (def = 5000)
 * -ap nnnn                  Set number of packets between acks to client (def = 1000)
 * -dbg                      Turn on verbose output");
 * -log filename             Set log file name (def=rrpserver)
 * -config file              If present, the configuration file contains the configuration of all of the links 
 *                           -ip, -f, -wait, -s can be on the line.  The -f and -ip arguments are required in the config file
 *</PRE>
 * @author davidketchum
 */
public class RRPServer extends Thread {
  private final int MODULO_SEQ=2000000000;
  private int port;                 // port to listen for connections on
  private ServerSocket d;           // The listen socket server
  private int totmsgs;
  private int updateFileMS;         // # of millis between file updates with latest block
  private int updateFileModulus;    // A modulus for updating the file (say every 10  or 100 packets);
  private int size[];               // Size of ring file in blocks (its actually one block bigger for ctl hdr)
  private int waitms[];
  private boolean terminate;
  private int ackModulus;           // Modulus of ack times
  private int ackMS;                // Time between acks
  //boolean firstAck;       // controls whether this is the first ack after a socket connection
  
  private String [] clientAddresses;
  private RRPServerHandler [] handlers;
  private String filename[];
  private boolean dbgbin;
  private boolean dbg;
  private String configFile;
  public void terminate() {terminate=true; interrupt();}   // cause the termination to begin
  public int getNumberOfMessages() {return totmsgs;}
  public final void prt(String s) {EdgeThread.staticprt(s);}
  public final void prta(String s) {EdgeThread.staticprta(s);}
  /** Creates a new instance of RRPServers 
   * @param args The command line arguments */
  public RRPServer( String [] args) {
    terminate=false;
    port = 22223;
    updateFileModulus=100000000;
    updateFileMS = 1000;
    ackModulus=1000;
    ackMS = 5000;
    dbg=false;
    dbgbin=false;
    String filenames="";
    String sizes="20000";
    String clientIP="";
    String waits="1000";
    try {
      //try  {
      for(int i=0; i<args.length; i++) { 

        if(args[i].equals("-p")) {port=Integer.parseInt(args[i+1]); i++;}
        else if(args[i].equals("") ) {}
        else if(args[i].equals("-tag")) {i++;}
        else if(args[i].equals("-f")) {filenames=args[i+1]; i++;}
        else if(args[i].equals("-u")) {updateFileMS = Integer.parseInt(args[i+1]); i++;}
        else if(args[i].equals("-up")) {updateFileModulus=Integer.parseInt(args[i+1]); i++;}
        else if(args[i].equals("-a")) {ackMS=Integer.parseInt(args[i+1]); i++;}
        else if(args[i].equals("-ap")) {ackModulus=Integer.parseInt(args[i+1]); i++;}
        else if(args[i].equals("-ip")) {clientIP = args[i+1];i++;}
        else if(args[i].equals("-dbg")) dbg=true;
        else if(args[i].equals("-dbgbin")) dbgbin=true;
        else if(args[i].equals("-log") || args[i].equals("-i") || args[i].equals("-instance")) i++;
        else if(args[i].equals("-s")) {sizes = args[i+1]; i++;}
        else if(args[i].equals("-wait")) {waits = args[i+1]; i++;}
        else if(args[i].equals("-config")) {configFile=args[i+1];i++;}
        else if(args[i].equals("-?") || args[i].indexOf("help")>0) {
          prt("-p nnnn     Set port number to something other than 22223");
          prt("-f file1:file2 Set the filename for the ring file");
          prt("-ip ip.adr:ip.adr  Set the only acceptable client source IP address");
          prt("-s nnnn:nnnn     Set the file size to nnnn MB (must divide evenly into 1,000,000");
          prt("-wait ms:ms2:... Set the ms to wait for each 256 blocks (def = 1000 = 1 mb/s)");
          prt("-u ms       Set interval between updates of ring metadata file to ms milliseconds (def=1000)");
          prt("-up ms      Set interval between udpates of ring meta data file  to ever ms packets (def=never)");
          prt("-a  nnnn    Set number of millseconds between acks to client (def = 5000)");
          prt("-ap nnnn    Set number of packets between acks to client (def = 1000)");
          prt("-dbg        Turn on verbose output");
          prt("-log file   Set log file name (def=rrpserver");
          prt("-?          Print this message");
          prt("-config file Get the -f, -ip, -s and -waut out of this file for each connections");
          prt("Other useful mode for watching an ring file java -cp RRPServer.jar gov.usgs.anss.net.Ringfile (no argument for usage)");
          System.exit(0);
        }
        else prt("argument "+args[i]+" is unknown to this program.  Try again!");
      }
      String ips[];
      String sz[];
      String wait[];
      if(configFile!=null)  {
        byte [] buf=null;
        prta("Reading config file : "+configFile);
        try {
          try (RandomAccessFile rw = new RandomAccessFile(configFile, "r")) {
            buf = new byte[(int) rw.length()];
            rw.seek(0L);
            rw.read(buf, 0, (int) rw.length());
          }
        }
        catch(IOException e) {
          Util.prta("COnfig file does not exist "+configFile);
          System.exit(0);
        }
        String content = new String(buf, 0, (int) buf.length);
        String [] lines = content.split("\\n");
        int ncomment=0;
        for (String line : lines) {
          if (line.charAt(0) == '#' || line.charAt(0) == '!') {
            ncomment++;
          }
        }
        int nlines = lines.length - ncomment;
        ips = new String[nlines];
        sz = new String[nlines];
        wait = new String[nlines];
        filename = new String[nlines];
        handlers = new RRPServerHandler[nlines];
        clientAddresses = new String[nlines];
        waitms = new int[nlines];
        size = new int[nlines];
        for(int i=0; i<nlines; i++) {sz[i] = "200"; wait[i] = "1000";}
        int j=0;
        for(int i=0; i<lines.length; i++) {
          if(lines[i].charAt(0) == '#' || lines[i].charAt(0) == '!') continue;
          args = lines[i].split("\\s");
          for(int k=0; k<args.length; k++) {
            if(args[k].equals("-ip")) {ips[j] = args[k+1].trim();k++;}
            else if(args[k].equals("")) {}
            else if(args[k].equals("-s")) {sz[j] = args[k+1]; k++;}
            else if(args[k].equals("-wait")) {wait[j] = args[k+1]; k++;}
            else if(args[k].equals("-f")) {filename[j] = args[k+1]; k++;}
            else Util.prt("Unknown argument in config file line="+i+" arg="+args[k]+"|");   
          }
          j++;
        }
      }
      else {
        // break the clientIP string into separate addresses and decode
        ips = clientIP.split(":");
        sz = sizes.split(":");
        wait = waits.split(":");
        waitms = new int[ips.length];
        size = new int[ips.length];
        filename = filenames.split(":");
        handlers = new RRPServerHandler[ips.length];
        clientAddresses = new String[ips.length];
        if(ips.length != sz.length || ips.length != filename.length) {
          prta("Configuration problem number of ip address and sizes and filenames must agree!");
          return;
        }
      }

      for(int i=0; i<ips.length; i++) {
        try {
          clientAddresses[i] = InetAddress.getByName(ips[i]).getHostAddress();
        }
        catch(UnknownHostException e) {
          prta("Client IP address is unknown = "+ips[i]);
        }
        size[i] = Integer.parseInt(sz[i])*2000;
        if(wait.length > i) waitms[i] = Integer.parseInt(wait[i]);
        else waitms[i] = 1000;
        if( MODULO_SEQ % size[i] != 0) {
          prt("Filesize does not divide evenly into 1,000,000.  Please correct line="+i);
          System.exit(0);
        }
        filename[i] = filename[i].replace("//","/");
        prta(i+" "+filename[i]+"/"+size[i]+" from "+clientAddresses[i]+" waitms="+waitms[i]+" size="+size[i]);
     }

      // Register our shutdown thread with the Runtime system.
      Runtime.getRuntime().addShutdownHook(new ShutdownRRPServer());
    }
    catch(RuntimeException e) {
      prta(" Runtime in RRPServer constructor!");
     e.printStackTrace(EdgeThread.staticout);
    }
    start();
  }
  
  /** listen for connections from the client, process them by creating new handler objects
   * and closing any old ones. 
   */
  @Override
  public void run() 
  { //dbg=false;
    
    // OPen up a port to listen for new connections.
    while(true) {
      try {
        //server = s;
        if(terminate) break;
        prta("RRPS: Open Port="+port);
        d = new ServerSocket(port);
        break;
      } 
      catch (SocketException e) 
      { if(e.getMessage().equals("RRPS:Address already in use")) 
        { 
            try {
              prta("RRPS: Address in use - try again.");
              Thread.sleep(2000);
            }
            catch (InterruptedException E) {
          }
        }
        else {
          prta("RRPS:Error opening TCP listen port ="+port+"-"+e.getMessage());
          try{
            Thread.sleep(2000);
          }
          catch (InterruptedException E) {

          }
        }
      }
      catch(IOException e) {
        prta("RRPS:Error opening socket server="+e.getMessage());
        try {
          Thread.sleep(2000);
        }
        catch (InterruptedException E) {

        }
      }
    }

    RRPServerHandler handler=null;
    while(true) 
    { if(terminate) break; 
      try {
        Socket s = d.accept();
        if(terminate) break;
        prta(" "+Util.ascdate()+" RRPS: from "+s+" # client addr="+clientAddresses.length);

        // Look for this address in list of addresses allowed, n=-1 if one is not found
        int n=-1;
        for(int i=0; i<clientAddresses.length; i++) {
          if(clientAddresses[i] != null) {
            if(s.getInetAddress().getHostAddress().equals(clientAddresses[i])) {
              n=i;
              break;
            }
          }
        }
        if(n == -1) {   // It was not found, reject the client
          prta("RRPS: connection from unexpected client rejected. got "+s.getInetAddress().getHostAddress());
          for (String clientAddresse : clientAddresses) {
            prt("Possibles : " + clientAddresse);
          }
          s.close();
          continue;
        }

        //firstAck = true;
        handler = handlers[n];
        if(handlers[n] != null) {
          prta("RRPS: close open handler "+handlers[n]);
          handlers[n].close();
          prta("RRPS: sleep for close");
          try{sleep(2000);} catch(InterruptedException e) {} // this should not be needed but some weird closes were being seen
        }
        prta("RRPS: start new handler n="+n+" "+filename[n]+" size="+size[n]+" wait="+waitms[n]);
        handlers[n] = new RRPServerHandler(s, filename[n], size[n], waitms[n]);
      }
      catch (IOException e)
      { prt("RRPS:receive through IO exception e="+e);
        e.printStackTrace();
      }
      catch(RuntimeException e ) {
        prta("Runtime error e="+e);
        e.printStackTrace( EdgeThread.staticout);
      }
    }       // end of infinite loop (while(true))
    //prt("Exiting RRPServers run()!! should never happen!****\n");
    prt("RRPS:read loop terminated");
  }
 
  private class ShutdownRRPServer extends Thread {
    public ShutdownRRPServer() {
      
    }
    
    /** this is called by the Runtime.shutdown during the shutdown sequence
     *cause all cleanup actions to occur
     */
    
    @Override
    public void run() {
      prt(Util.asctime()+" RRPS: Shutdown detected.  Close all of the handlers");
      for (RRPServerHandler handler : handlers) {
        if (handler != null) {
          handler.updateControl(); // always write out latest info on the way down.
          handler.close();
        }
        terminate=true;
        interrupt();
        prt(Util.asctime()+"RRPS:Shutdown Done.");
        try{
          d.close();
        }
        catch(IOException e) {
          prt(Util.asctime()+"IOException trying to close ringfile or closing socket="+e.getMessage());
        }
      }
    }
  }
  public static String safeLetter(byte b) {
    char c = (char) b;
    return Character.isLetterOrDigit(c) || c == ' ' ? ""+c : Util.toHex((byte) c);
  }
  public static String toStringRaw(byte [] buf) {
    ByteBuffer bb = ByteBuffer.wrap(buf);
    StringBuilder tmp = new StringBuilder(100);
    bb.position(0);
    for(int i=0; i<6; i++)  tmp.append(safeLetter(bb.get()));
    tmp.append(" ");
    bb.position(18);
    for(int i=0; i<2; i++) tmp.append(safeLetter( bb.get()));
    bb.position(8);
    for(int i=0; i<5; i++) tmp.append(safeLetter( bb.get()));
    bb.position(15);
    for(int i=0; i<3; i++) tmp.append(safeLetter( bb.get()));
    bb.position(13);
    for(int i=0; i<2; i++) tmp.append(safeLetter( bb.get()));
    bb.position(20);
    short i2 = bb.getShort();
    tmp.append(" ").append(i2).append(" ").append(Util.toHex(i2));
    i2 = bb.getShort();
    tmp.append(" ").append(i2).append(" ").append(Util.toHex(i2));
    tmp.append(" ").append(bb.get()).append(":").append(bb.get()).append(":").append(bb.get());
    bb.get();
    i2 = bb.getShort();
    tmp.append(".").append(i2).append(" ").append(Util.toHex(i2));
    i2 = bb.getShort();
    tmp.append(" ns=").append(i2);
    i2 = bb.getShort();
    tmp.append(" rt=").append(i2);
    i2 = bb.getShort();
    tmp.append("*").append(i2);
    bb.position(39);
    tmp.append(" nb=").append(bb.get());
    bb.position(44);
    i2 = bb.getShort();
    tmp.append(" d=").append(i2);
     i2 = bb.getShort();
    tmp.append(" b=").append(i2);
    return tmp.toString();
  }
  
  /** object of this class handle input from a single client to a raw disk file */
  class RRPServerHandler extends Thread {
    Socket s;
    String tag;
    byte [] buf;
    ByteBuffer bbuf;      // data buffer for blocks read from inet
    byte [] ackbuf;
    ByteBuffer ack;
    //OutputStream out;
    //InputStream in;
    boolean terminate;
    boolean firstAck;
    int size;
    String filename;
    RawDisk dsk;
    boolean diskOpen;
    byte [] ctlbuf;
    ByteBuffer ctlbb;       // Wrap the ctrl file buffer
    int nextout;            // next block we expect to write to disk
    int lastAckNextout;     // Last block in file acked in protocol
    byte [] b;              // Data for control block in ring file
    ByteBuffer bb;          // Bitye for for control block in ring file
    long lastAckUpdate;     // Time of last ack update.
    long lastControlUpdate; // Last system time the control block was updated.
    int wait;               // Millis between each 256 blocks
    long lastBlockCheck;
    long lastStatusUpdate;
    RRPHandlerWatchdog watchdog;
    public void close() {
      terminate=true;
      //if(dbg) 
        prta(tag+"    *** Closing socket s="+(s == null? "null" : s.toString())+" isclosed() ="+s.isClosed());
      updateControl();
      try {
        diskOpen=false;
        dsk.close();
      }
      catch(IOException e) {
        prta(tag+"IOError closing disk "+filename);
      }
      if(s != null) 
        if(!s.isClosed()) {
          try {
            if(dbg) prta(tag+"    *** actual close s="+s);
            s.close();
          }
          catch(IOException e) {
            prta(tag+" IOError closing connection");
          }
        }
    }
    /** send and ack for current position */
    private void sendAck() {
      if(firstAck) {                  // First acks need to respond with special command or desired 
        firstAck=false;
     
        if(lastAckNextout == -1) {      // New file, we do not know where
          prta(tag+" Ack Startup files must be new, ask for all data");
          ack.position(4);
          ack.putInt(-2);
          ack.putInt(0);
        }
        else {
          prta(tag+" Ack Startup : old file, send first desired sequence="+nextout);
          if(nextout >= MODULO_SEQ) nextout -= MODULO_SEQ;
          prta(tag+" Ack startup : old file nextout="+nextout);
          ack.position(4);
          ack.putInt(nextout);
          ack.putInt(nextout);
          lastAckNextout = nextout;
        }
        
      }       // normal running ack
      else {
        ack.position(4);
        ack.putInt(lastAckNextout);
        int last = nextout - 1;
        if(last < 0) 
          last += MODULO_SEQ;
        ack.putInt(last);
        if(last == lastAckNextout) {
          if(dbg) prta(tag+" Ack suppressed same sequence ="+last);
          return;       // do not send same unless first time!
        }
        if(dbg) prta(tag+"    * Ack for "+lastAckNextout+"-"+last);
      }
      try {
        s.getOutputStream().write(ackbuf,0,12);
        lastAckUpdate = System.currentTimeMillis();
        
      }
      catch(IOException e) {
        prt(tag+"RRPH: sendAck() gave IOException.  Close connection! e="+e.getMessage());
        close();
      }
      lastAckNextout=nextout;
    }
    public RRPServerHandler(Socket ss, String file, int sz, int waitms) {
      s = ss;
      size=sz;
      filename=file;
      wait = waitms;
      tag = "RRPH:"+s.getInetAddress().getHostAddress()+"/"+s.getPort()+":";
      prt(tag+" handler started file="+file+" sz="+sz+" watims="+waitms);
      buf = new byte[512];
      bbuf = ByteBuffer.wrap(buf);
      ackbuf = new byte[12];
      ack = ByteBuffer.wrap(ackbuf);
      ack.position(0);
      ack.putShort((short) 0xa1b2);
      ack.putShort((short) 0);
      lastControlUpdate=System.currentTimeMillis();
      firstAck=true;
      ctlbuf = new byte[512];
      ctlbb = ByteBuffer.wrap(ctlbuf);
      for(int i=0; i<512; i++) ctlbuf[i]=0;
      try {
        dsk = new RawDisk(filename,"rw");
        diskOpen=true;
        //ctldsk = new RawDisk(filename+".ctl","rw");
      }
      catch(FileNotFoundException e) {
        prt(tag+" Could not open the ring file or its control file e="+e.getMessage());
        System.exit(0);
      }
      b = new byte[512];
      for(int i=0; i<512;i++) b[i]=0;
      bb = ByteBuffer.wrap(b);
      try {
        boolean doInit=false;
        if(dsk.length() == 0) {   // is it a new file
          doInit=true;
          lastAckNextout=-1;
          nextout=-1;
        }
        else {                    // existing file, check its length and get nextout and size
          dsk.readBlock(b,0,512);
          bb.clear();
          bb.position(0);
          nextout= bb.getInt();       // get the first sequence from header
          int sizenow = bb.getInt();
          lastAckNextout=bb.getInt();
          //if(size != sizenow) doInit=true;
          if(size > sizenow) {
            dsk.writeBlock(size, b, 0, 512); // make it bigger
            lastAckNextout=nextout;           // we are going to start at last block processed
          }
          if(size != sizenow) {
            try {
              prta(tag+" Make file bigger sizenow="+sizenow+" size="+size+" newsize="+(((long) size+1)*512));
             
              try {sleep(250);} catch(InterruptedException e) {}
              dsk.setLength(((long) size+1)*512);
              lastAckNextout=nextout;
            }
            catch(Exception e) {
              prta(tag+" making file bigger size="+size);
              e.printStackTrace(EdgeThread.staticout);
            }
          }
        }
        // the file is new or changed size, clear it out since the blocks no long align with sequence
        if(doInit) {
          bb.clear();
          bb.putInt(nextout);
          bb.putInt(size);      // in blocks
          bb.putInt(lastAckNextout);
          dsk.writeBlock(0, b, 0,512);
          for(int i=0; i<512;i++) b[i]=0;
          prt(Util.asctime()+" "+tag+" New Ring file - zero it. size="+size);
          byte [] zerobuf = new byte[51200];
          int blk=1;
          while(blk < size+1) {
            int nblk = size+1-blk;
            if(nblk > 100) nblk=100;
            dsk.writeBlock(blk, zerobuf, 0, zerobuf.length);
            blk+= nblk;
          }
          //zerobuf=null;
          prt(Util.asctime()+" "+tag+" New Ring initialized.");
        }
      }
      catch(IOException e) {
        Util.IOErrorPrint(e,tag+" getting size or reading data from ring file e="+e.getMessage());
        System.exit(0);
      }
      prta(tag+"New RRPHandler "+filename+" size="+size+" waitms="+wait);
      start();
    }
    @Override
    public void run() {
      try {
        //out = s.getOutputStream();
        //in = s.getInputStream();  
        sendAck();                  // Send startup ack
        int len;
        int nchar=0;
        long waitfor;
        int l;
        short chksum;
        int seq;
        lastStatusUpdate=System.currentTimeMillis();
        lastBlockCheck = lastStatusUpdate;
        int lastNextout=nextout;
        watchdog = new RRPHandlerWatchdog(this);

        // Starting the socket - send an ack packet to set up the sequence numbers
        while(!terminate) { 
          // body of Handler goes here
          len = 8;
          l=0;
          while(len > 0) {            //
            //while(s.getInputStream().available() <= 0 && !s.isClosed()) try{sleep(10);} catch(InterruptedException e) {}
            nchar= s.getInputStream().read(buf, l, len);// get nchar
            if(nchar <= 0) break;     // EOF - close up
            l += nchar;               // update the offset
            len -= nchar;             // reduce the number left to read
          }
          if(terminate) break;
          //prta("read got "+nchar);
          //for(int i=0; i<8; i++ ) prt(i+" = "+Util.toHex(buf[i]));
          if(nchar <= 0) break;       // EOF exit loop
          // Get header, flags and sequence
          bbuf.position(0);
          if(bbuf.getShort() != (short) 0xa1b2) {
            bbuf.position(0);
            prt(tag+" Leadins not right. close up."+Util.toHex(bbuf.getShort()));
            terminate=true;
            break;
          }
          chksum = bbuf.getShort();
          seq = bbuf.getInt();
          
          // Start chksum compute on the sequence
          short chk = 0;
          for(int i=4; i<8;i++) chk += buf[i];
          if(nextout >= MODULO_SEQ) nextout -= MODULO_SEQ;
          if(seq != nextout  && seq >= 0) {
            prt(tag+" Sequence out of order got "+seq+" expecting "+nextout);
            terminate=true;
            break;
          } 
          
          // If sequence is negative, this is a "null" ack and we need to reset our expectations
          if(seq < 0) {
            nchar= s.getInputStream().read(buf,0,4);
            l=4;
            if(nchar == 4) {
              bbuf.position(0);
              nextout=bbuf.getInt();
              prta(tag+" Got Null packet set seq to "+nextout);
              lastAckNextout = nextout-1;
              sendAck();
              continue;
            }
          }
          //  This is a normal payload packet
          else {
            len = 512;
            
            l=0;
            while(len > 0) {            // 
              nchar= s.getInputStream().read(buf, l, len);// get nchar
              if(nchar <= 0) break;     // EOF - close up
              l += nchar;               // update the offset
              len -= nchar;             // reduce the number left to read
            }
          }
          
          // CHeck for a chksum error
          for(int i=0; i<l; i++) chk += buf[i];
          //if(dbg) prt(Util.asctime()+"    * Got data block seq="+seq+" ck="+chksum+" "+chk);
          if(chk != chksum) {
            prta(tag+" Checksum do not agree nextout="+nextout+" chk="+Util.toHex(chk)+" != "+Util.toHex(chksum));
            terminate=true;
            close();
          }
          else {
            if(dbgbin) {        // check that the sequence agrees with the seq in header
              bbuf.position(0);
              int seqhdr = bbuf.getInt();
              if(seqhdr != seq) {
                prta(tag+" Seq in data block does not agree with seq delivered! hdr="+seqhdr+" != "+seq);
              }
            }
            if(dbg) prta(tag+" rcv sq="+seq+" "+toStringRaw(buf));
            // The data packet is good, write it out
            dsk.writeBlock( (seq % size)+1, buf, 0, 512);
            if(nextout >= MODULO_SEQ) nextout=0;
            if(seq >= 0) nextout = seq+1;
            if(nextout >= MODULO_SEQ) nextout -= MODULO_SEQ;

            // If it is time, write out the ctl block
            long now = System.currentTimeMillis();
            if(now - lastControlUpdate > updateFileMS || seq % updateFileModulus == 0)  
              updateControl();
            if(now - lastAckUpdate > ackMS || seq % ackModulus == 0) sendAck();      // send acks every so often
            if(now - lastStatusUpdate > 600000) {
              prta(tag+" nextout="+nextout+" lastAck="+lastAckNextout+" nb="+
                      (nextout-lastNextout)*520/1000+" kB");
              lastNextout = nextout;
              lastStatusUpdate=now;
            }
            // check for time to wait
            if(seq % 256 == 200) {
              waitfor = now - lastBlockCheck;
              lastBlockCheck = now;
              if((seq % 25600) == 200 || waitfor < wait) 
                prta(tag+"Waiting seq="+seq+" "+(256*520*8/waitfor)+" kbps elapsed="+waitfor+" waitms="+wait+" "+
                      (waitfor < wait?" * "+(wait-waitfor)+"ms":""));
              if(waitfor < wait) {
                try{
                  sleep(Math.max(1, wait - waitfor));
                } catch(InterruptedException e) {}
                lastBlockCheck=System.currentTimeMillis();
              }
            }
          }
        }         // end while(!terminate) 
        if(nchar < 0) prt(tag+" RRPH: EOF found.  close up socket");
      }
      catch(IOException e) {
        Util.IOErrorPrint(e,tag+" RRPH: IOError on socket");
      }
      if(s != null) close();      // This updates the control block
      prta(tag+" RRPH: RRPServerHandler has exit on s="+s);
    }
    /** write out the control block with current values */
    private void updateControl() {
      if(dbg) prta(tag+"    * Update control block nextout="+nextout+" size="+size+" lastAck="+lastAckNextout);
      if(!diskOpen) {prta(tag+"  * Update control called after disk closed.  continue"); return;}
      ctlbb.position(0);
      ctlbb.putInt(nextout);
      ctlbb.putInt(size);
      ctlbb.putInt(lastAckNextout);
      try {
        dsk.writeBlock( 0, ctlbuf, 0, 512);
      }
      catch(IOException e) {
        prta(tag+" IOException updating control block ="+e.getMessage());
      }
      lastControlUpdate = System.currentTimeMillis();
    }

    
  }         // End of RRPHandler class
  class RRPHandlerWatchdog extends Thread {
    RRPServerHandler thr;
    int watchNextout;
    int nsec;
    public RRPHandlerWatchdog(RRPServerHandler t) {
      thr=t;
      watchNextout = thr.nextout;
      nsec=1200;
      setDaemon(true);
    }
    @Override
    public void run() {
      while(!terminate) {
        for(int i=0; i<nsec; i++) {
          try {sleep(1000);} catch(InterruptedException e) {}
          if(terminate) break;
        }
        if(terminate) break;
        nsec=300;         // After any success, set shorter watch intervals
        prta(thr.tag+" check watchdog "+thr.nextout+" to "+watchNextout);
        if( Math.abs(thr.nextout - watchNextout) < 5) {
          prta(thr.tag+" *** RRPHandlerWatchdog went off nextout="+thr.nextout+
                  " watchNextout="+watchNextout+" closing socket");
          thr.close();
          nsec=1200;      // connection broken - need to wait longer
          break;
        }
        watchNextout=thr.nextout;
      }
    }
  }
  

  /** This main just sets up the Util package and starts up a RRPServer object.
  * @param args the command line arguments
  */

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    String logfile="rrpserver";
    for(int i=0; i<args.length; i++) if(args[i].equals("-log")) logfile=args[i+1];
    EdgeThread.setMainLogname(logfile);
    
    EdgeThread.staticprt(Util.asctime());
    RRPServer t = new RRPServer(args);

  }  
  
}
