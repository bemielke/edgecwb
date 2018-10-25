/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */




package gov.usgs.anss.edge;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class ClientClientCopier extends Thread {
  private Socket ss;
  private Socket sout;
  private OutputStream out;
  private InputStream in;
  private boolean terminate;
  private String hostin, hostout, tag;
  private int portin, portout;
  private long nbytesOut,lastNbytesOut;
  private boolean dbg;
  private ShutdownClientClient shutdown;
    /**write packets from queue*/
  public ClientClientCopier(String argline, String tg) {
    tag=tg;
    String [] args = argline.replaceAll("  "," ").replaceAll("  "," ").split("\\s");
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-hin")) {hostin=args[i+1]; i++;}
      else if(args[i].equals("-hout")) {hostout=args[i+1]; i++;}
      else if(args[i].equals("-pin")) {portin=Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-pout")) {portout=Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-tag")) {tag = args[i+1]; i++;}
      else Util.prta(tag+" unknown switch "+i+" = "+args[i]);
    }
    shutdown = new ShutdownClientClient();
    Runtime.getRuntime().addShutdownHook(shutdown);
    Util.prta(tag+" Start "+argline);
    start();
  }
  @Override
  public void run() {
    boolean connected;
    byte [] buf = new byte[1000];
    long lastStatus;
    // Create a timeout thread to terminate this guy if it quits getting data
    while(!terminate) {
      connected = false;
      // This loop establishes a socket
      while( !terminate) {
        try { 
          Util.prta(tag+" Try new socket to "+hostout+"/"+
              portout+" dbg="+dbg);
          sout = new Socket(hostout, portout);
          Util.prta(tag+" Created new socket to "+hostout+"/"+
              portout+" dbg="+dbg);
        }
        catch(UnknownHostException e) {
          Util.prta(tag+" Unknown host for socket="+hostout+"/"+portout);
          try {sleep(300000);} catch(InterruptedException e2) {}
        }
        catch(IOException e) {
          if(e.getMessage().indexOf("Connection refused") >= 0) {
            Util.prta(tag+" ** Connection refused to "+hostout+"/"+portout+" try again in 60");
          }
          else if(e.toString().indexOf("Connection timed out")>= 0) {
            Util.prta(tag+" ** Connection timed out to "+hostout+"/"+portout+" try again in 60");

          }
          else
            Util.IOErrorPrint(e,tag+" *** IOError opening client "+hostout+"/"+portout+" try again in 30");
          try {sleep(60000);} catch(InterruptedException e2) {}
          continue;
        } 
        try {
          ss = new Socket(hostin, portin);
          Util.prta(tag+" Created new socket to "+hostin+"/"+
              portin+" dbg="+dbg);
          if(terminate) break; 
          out = sout.getOutputStream();
          in = ss.getInputStream();
          connected=true;
          break;
        }
        catch(UnknownHostException e) {
          Util.prta(tag+" Unknown host for socket="+hostin+"/"+portin);
          try {sleep(300000);} catch(InterruptedException e2) {}
        }
        catch(IOException e) {
          if(e.getMessage().indexOf("Connection refused") >= 0) {
            Util.prta(tag+" ** Connection refused to "+hostin+"/"+portin+" try again in 30");
          }
          else
            Util.IOErrorPrint(e,tag+" *** IOError opening client "+hostin+"/"+portin+" try again in 30");
          try {sleep(30000);} catch(InterruptedException e2) {}
        } 
      }

      // Both sockets are open, in for the reader out for the writer
      lastStatus = System.currentTimeMillis();
      int nbytes=0;
      int l;
      while(!terminate) {
        try {
          // read until the full length is in 
          while (in.available() <=0 && !terminate) {
            try{sleep(100);
            } 
            catch(InterruptedException e) {}
          }
          if(terminate) break;
          l = in.read(buf, 0, Math.min(buf.length, in.available()));
          if(l > 0) {
            out.write(buf, 0, l);
            nbytes += l;
            nbytesOut += l;
          }
          if(l == -1) {
            Util.prta(tag+" EOF on input - remake connections");
            break;
          }

          // put the block in the queue
          if(terminate) break;

          if( System.currentTimeMillis() - lastStatus > 60000) {

            Util.prta(tag+" "+nbytes+" bytes");
            nbytes=0;
            lastStatus=System.currentTimeMillis();
          }
          try{sleep(100);} catch(InterruptedException e) {}
        }
        catch(IOException e) {
          if(e.getMessage().indexOf("Operation interrupted") >=0) {
            Util.prta(tag+" ** has gotten interrupt, shutdown socket:"+e.getMessage());
          }
          else {
            Util.prta(tag+" Writing data to "+hostin+"/"+portin+" to "+hostout+"/"+portout+" e="+e);
            e.printStackTrace();
          }
          break;    // this forces openning new sockets
        }
        //Util.prta(tag+"timer");
      }             // esand of while(!terminate) on writing data
      try {
        if(ss != null)
          if(!ss.isClosed()) ss.close();
        ss=null;
      }
      catch(IOException e2) {}
      try {
        if(sout != null)
          if(!sout.isClosed()) sout.close();
        sout=null;
      }
      catch(IOException e2) {}
      Util.prt(tag+" Bottom of outside loop.  Reopen the sockets");
      try{sleep(1000L);} catch (InterruptedException e) {}
    }               // Outside loop that opens the sockets
    // We have been terminated
    Util.prta(tag+" ** terminated ");
    if(!ss.isClosed()) {       // close our socket with predjudice (no mercy!)
      try {
        ss.close();
      }
      catch(IOException e2) {}
    }
    if(!sout.isClosed()) {       // close our socket with predjudice (no mercy!)
      try {
        sout.close();
      }
      catch(IOException e2) {}
    }
    Util.prta(tag+"  ** exiting");
  }
  public class ShutdownClientClient extends Thread {
    public ShutdownClientClient () {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown "+getName()+" "+getClass().getSimpleName());
    }
    @Override
    public void run() {
      terminate=true;
      if(ss != null) 
        if(!ss.isClosed()) {       // close our socket with predjudice (no mercy!)
        try {
          ss.close();
        }
        catch(IOException e2) {}
      }
      if(sout != null)
      if(!sout.isClosed()) {       // close our socket with predjudice (no mercy!)
        try {
          sout.close();
        }
        catch(IOException e2) {}
      }
    }
  }
  public class CheckProgress extends Thread {
    public CheckProgress() {
      start();
    }
    public void run() {
      while(!terminate) {
        try{sleep(60000);} catch(InterruptedException e) {}
        if(lastNbytesOut == nbytesOut) {
          try {
            if(ss != null) ss.close();
            
          }
          catch(IOException e) {}
          Util.prta("No data flow.  Exit!!!="+nbytesOut+" "+lastNbytesOut);
          System.exit(1);
        }
        Util.prt("O.K. nbytes="+nbytesOut+" "+lastNbytesOut);
        lastNbytesOut = nbytesOut; 
      }
    }
  }
  public static void main(String [] args) {
    Util.setProcess("UdpChannel");
    EdgeProperties.init();
    Util.setModeGMT();
    EdgeThread.setMainLogname("udpchannel");   // This sets the default log name in edge thread (def=edgemom!)
    Util.setNoconsole(false);      // Mark no dialog boxes
    Util.setNoInteractive(true);
    Util.prta(Util.ascdate()+" start ClientClientCopier");
    String argline = "";
    for(int i=0; i<args.length;i++) argline += args[i]+" ";
    argline = argline.trim();
    ClientClientCopier copy = new ClientClientCopier(argline,"CCC:");
    for(;;) {
      if(!copy.isAlive()) break;
      Util.sleep(10000);
    }
  }
}

