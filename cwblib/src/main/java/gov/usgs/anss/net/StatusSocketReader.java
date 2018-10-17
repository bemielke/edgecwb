/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.net;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.util.StatusInfo;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.sql.*;
import java.util.ArrayList;

/**
* StatusSocketReader.java
*
*  This object connects a socket which has input StatusInfo packets.  Normally
 * it connects to a ServerThread/UdpProcess pair.  These other tasks are gathering
 * UDP based Status information and distributing them to Clients like us which want
 * a copy of the information via socket.
 *
 * the "Class" is a data class implementing StatusInfo and Comparable (ex. ProcessStatus)
 * the port is the UDP port number to listen  on
 * This ServerThread contains all the details to pass the information to connecting
 * clients.
 * <p>
 * If this class is run by itself, it can be used to dump the current information from the UdpChannel process.
 * <p>
 * java -cp $PATHTOJARS/cwblib.jar gov.usgs.anss.net.StatusSocketReader [-h ip][-p port][-re regexp]
 *
* @author  ketchum
*/
public class StatusSocketReader extends Thread  {
  private Socket d;                         // The currently open socket
  private StatusInfo [] msgs;
  private int msgLength;                    // The raw data length of the class type
  private final int port;                         // Port we connected!
  private final String host;                      // Host we are connected to
  private int nmsgs;                        // number of active elements in msgs
  private int countMsgs;
  private final Class objClass;                   // The class of the status message (a StatusInfo)
  private Constructor emptyConstructor;     // This can create a objClass with emtpy args
  private Constructor bufferConstructor;    // This can create an objClass with a Datagram input
  private long lastStatus;     // Time last status was received
  private InputStream in;
  private OutputStream out;
  private String tag;
  private final CheckProgress checker;
  private boolean isConnected;
  private EdgeThread par;
  public void setParent(EdgeThread parent) {par = parent;}
  private void prt(String a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prt(StringBuilder a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prta(String a) {if(par == null) Util.prta(a); else par.prta(a);}
  private void prta(StringBuilder a) {if(par == null) Util.prta(a); else par.prta(a);}    
  /** Creates a new instance of ConnectionStatus with all Z's in the ke
   * @return The socket
*/
  public Socket getSocket() {return d;}
  public void appendTag(String s) {tag += s;}
  public int getCountMsgs(){ return countMsgs;}
  public int getNmsgs() {return nmsgs;}
  public boolean isConnected() {return isConnected;}
  private StringBuilder monitorsb = new StringBuilder(100);
  public StringBuilder getMonitorString() {
    if(monitorsb.length() > 0) monitorsb.delete(0, monitorsb.length());
    monitorsb.append("SSRNmssgs=").append(nmsgs).append("\nSSRCount=").append(countMsgs).append("\n");
    return monitorsb;
  }
  @Override
  public String toString() {return tag+" tot="+countMsgs+" nmsg="+nmsgs+" closed="+(d == null?"null":d.isClosed())+
      " class="+objClass.getSimpleName()+"run="+isAlive()+" s="+(d==null?"null":d.getInetAddress()+"/"+d.getPort());}
  /** Creates a new instance of StatusSocketReader running on port port
  * @param cl the "Class" is a data class implementing StatusInfo and Comparable (ex. ProcessStatus)
   *@param h The host to which to connect.
  * @param pt the port to connect to and then read StatusInfo.
  */
  public StatusSocketReader(Class cl, String h,int pt) {
    this(cl, h, pt, null);
  }  
  /** Creates a new instance of StatusSocketReader running on port port
  * @param cl the "Class" is a data class implementing StatusInfo and Comparable (ex. ProcessStatus)
   *@param h The host to which to connect.
  * @param pt the port to connect to and then read StatusInfo.
  * @param parent An EdgeThread for logging
  */
  public StatusSocketReader(Class cl, String h,int pt, EdgeThread parent) {
    par = parent;
    port=pt;
    host=h;
    objClass = cl;
    countMsgs=0;
    tag = "SSR["+getName().substring(getName().indexOf("-")+1)+"]:";
    try {
      Class [] em = new Class[0];
      emptyConstructor = objClass.getConstructor(em);
      Method getLength = objClass.getMethod("getMaxLength", em);
      //prta(tag+" getLength() found="+getLength);
      Integer tmp = (Integer) getLength.invoke(null, (Object []) null);
      msgLength = tmp;
      //prt(tag+" Max Length discovered="+msgLength);
      byte [] bb = new byte [msgLength];
      em = new Class[1];
      em[0]= bb.getClass();     // An array of bytes
      bufferConstructor = objClass.getConstructor(em);
      //prta(tag+" got constructor="+bufferConstructor+" and "+emptyConstructor);
    } catch (IllegalAccessException e) {
      prta(tag+" Illegal Access exception system.exit="+e.getMessage());
        e.printStackTrace();
        Util.exit(0);
    } catch (InvocationTargetException e) {
      prta(tag+" Invocation Target Exception system.exit"+e.getMessage());
        e.printStackTrace();
        Util.exit(0);
    } /*catch (ClassNotFoundException e) {
      prta(tag+" Class not found!");
        e.printStackTrace();
        Util.exit(0);
    }*/
    catch (NoSuchMethodException e) {
        prt(tag+" No such method constructor found for class= system.exit"+cl);
        e.printStackTrace();
        Util.exit(0);
    } catch (SecurityException e2) {
        prt(tag+" Security Exception getting constructors!!!! system.exit");
        e2.printStackTrace();
        Util.exit(0);
    }
    checker = new CheckProgress(this);    // Thread to monitor for dead sockets.
    prta(tag+" "+getName()+" "+host+"/"+port+" "+objClass.getSimpleName()+" starting max len="+msgLength);
    this.setDaemon(true);
    start();
  }
  /** This Thread keeps a socket open to the host and port, reads in any information
   * on that socket, keeps a list of unique StatusInfo, and updates that list when new
   * data for the unique key comes in.
   */
  @Override
  public void run() {
    boolean dbg=false;
    byte [] buf= new byte[msgLength];
    Object [] emptyArgs= new Object[0];
    StringBuilder sb = new StringBuilder(2000);
    int insertAt;
    while(true) {
      try {       // catch runtime exceptions!
        /* keep trying until a connection is made */
        while(true) {
          isConnected=false;
          try {
            prta(tag+" SSR: Open Port="+host+"/"+port);
            d = new Socket(host, port);
            in = d.getInputStream();        // Get input and output streams
            out =d.getOutputStream();

            // Build first 100 StatusInfo objects and fill with empty data
            msgs = new StatusInfo[1000];          // Number of msgs we can track
            nmsgs=0;
            for(int i=0; i<1000; i++) {
              msgs[i]= (StatusInfo) emptyConstructor.newInstance(emptyArgs);
              msgs[i].setParent(par);
            }//new ProcessStatus();
            lastStatus = System.currentTimeMillis();
            break;
          }
          catch (UnknownHostException e) {
            prt(tag+" Host is unknown="+host+"/"+port);
            //Util.exit(0);
          }
          catch (IOException e) { 
            if(e.getMessage().contains("Connection refused")) {
              prt("Connection refused to "+host+"/"+port);
              try{sleep(30000);} catch(InterruptedException e2) {}
              continue; 
            }
            Util.IOErrorPrint(e,tag+" IO error opening socket="+host+"/"+port);
            //Util.exit(0); 
          } 
          catch (InvocationTargetException e) {
            prt(tag+" InvocationTargetException : This is impossible!");
            //Util.exit(0);
          } 
          catch (IllegalAccessException E) {
            E.printStackTrace();
            prt(tag+" Illegal Access exception.  Impossible!");
            //Util.exit(0);
          } 
          catch(InstantiationException e2) {
            e2.printStackTrace();
            prt(tag+" instantiationException is impossible!");
            //Util.exit(0);
          }
          try {
            sleep((long) 20000);
          } 
          catch (InterruptedException e) {}
        }   // While True on opening the socket
        isConnected=true;
        prta(tag+" run init code");
        // Read data from the socket and update/create the list of records 
        StatusInfo ps = null;
        Object [] args = new Object[1];
        args[0]=buf;
        long now;  
        int len;
        int ins;
        while(true) {
          try {
           while(in.available() < msgLength && isConnected) Util.sleep(1);
            if(!isConnected) break;
            len= Util.readFully(in, buf, 0, msgLength);
            // len = in.read(buf);
            if(len != msgLength) {
              prt(tag+" **** read wrong length="+len+" should be "+msgLength);
              if(len < msgLength) {
                int l = in.read(buf, len, msgLength-len);
                if(l != msgLength-len) {
                  prt(tag+" **** Read short did not recover- rebuild socket! l="+l+
                      " should be "+(msgLength-len));
                  break;
                }
              }
            }
            //prt(Util.toAllPrintable(new String(buf, 0, len)));
            if(len <= 0) break;             // EOF - close up
            synchronized (this) {
              countMsgs++;
              now = System.currentTimeMillis();
              if(ps == null) {
                ps = (StatusInfo) bufferConstructor.newInstance(args);
                ps.setParent(par);
              }// new ProcessStatus(p);
              else ((StatusInfo) ps).reload(buf);
              ins = Arrays.binarySearch(msgs, 0, nmsgs, ps);
              if(ins >= 0) {
                ((StatusInfo) msgs[ins]).update(ps);    // exists, just use update
                if(dbg) prt(tag+" Update rec="+ins+" "+msgs[ins]);
              } 
              else {
                nmsgs=nmsgs+1;
                if(nmsgs > msgs.length-10) {    // expand messages before it gets full
                  StatusInfo [] tmp = new StatusInfo[msgs.length+100];
                  System.arraycopy(msgs, 0, tmp, 0, msgs.length);
                  for(int i=0; i<100; i++) {
                    tmp[msgs.length+i] = (StatusInfo) emptyConstructor.newInstance(emptyArgs);
                    tmp[msgs.length+i].setParent(par);
                  }
                  msgs = tmp;
                  if(msgs.length % 2000 == 0) prta(tag+" Expand msgs len="+msgs.length+" nmsgs="+nmsgs);
                }
                /**  DEBUG : look for mis-insertion.  will not work except in ChannelDislay*/
                /*if(msgs[0].getClass().getName().equals("ChannelStatus"))
                  for(int j=0; j<nmsgs; j++) 
                    if( ((ChannelStatus)msgs[j]).getKey().equals(((ChannelStatus)ps).getKey())) {
                      prt(tag+" *** found match at "+j);
                      dbg=true;
                    }*/
                

                insertAt = -(ins +1);
                if(dbg || insertAt >= msgs.length)
                  prt(tag+" Insert record at "+insertAt+"/"+nmsgs+" ps="+ps.toString());
                if(insertAt+1 < msgs.length)
                  System.arraycopy(msgs, insertAt, msgs, insertAt+1, msgs.length-insertAt-1);
                msgs[insertAt]=ps;
                ps = null;
              }
            }     // end of synchronize(this) 
            /*if(dbg)
            {
              if(sb.length() > 0) sb.delete(0, sb.length());
              for(int i=0; i<msgs.length; i++)
              {
                sb.append(i).append("=").append(((StatusInfo) msgs[i]).getKey());
                if(i % 4 == 3)
                {
                  prt(sb.toString());
                  sb.delete(0,sb.length());
                }
              }
              //debug: exit if we ever turn on debugging 
              Util.exit(0);
            }*/

            // Is it time for status yet
            if( (now - lastStatus) > 300000) {
              prta(this.getName()+" "+host+"/"+port+" "+objClass.getSimpleName()+" SSR: # Rcv="+countMsgs+" object="+nmsgs);
              countMsgs=0;
              lastStatus = now;
            }

          }
          catch (IOException e) {
            Util.SocketIOErrorPrint(e,tag+" receive IO error");
            break;      // Drop out of read loop to connect looop
          }
          catch (InvocationTargetException e) {
            e.printStackTrace();
            prt(tag+" InvocationTargetException : This is impossible1! system.exit");
            Util.exit(0);
          } 
          catch (IllegalAccessException E) {
            E.printStackTrace(); 
            prt(tag+" Illegal Access exception.  Impossible1! system.exit");
            Util.exit(0);
          } catch(InstantiationException e2) {
            prt(tag+" instantiationException is impossible1! system.exit");
            e2.printStackTrace();
            Util.exit(0);
          }

        }     // while(true) Get data
        try {
          prta(tag+" closing socket - exited while(true) for read ");
          if(!d.isClosed()) d.close();
        } catch (IOException e) {
          Util.SocketIOErrorPrint(e,tag+" Closing SSR socket- reopen");
        }
      }
      catch(RuntimeException e ) {
        
        prta(tag+" RuntimeException in StatusSocketReader e="+e.getMessage());
        e.printStackTrace();
        if(!d.isClosed()) {
          try{
            d.close();
          }
          catch(IOException e2) {}
        }
      }
    }       // while(true) do socket open
  }
  public void reopen() {
    isConnected=false;
    prta(tag+" ***** SSR doing reopen");
    if(getSocket() != null)
      if(!getSocket().isClosed()) {
        try {
          getSocket().close();
        }
        catch(IOException e) {
          prta(tag+" Check progress saw IOError on close e="+e);
        }
      }

  }
  public class CheckProgress extends Thread {
    StatusSocketReader thr;
    public CheckProgress(StatusSocketReader t) {
      thr=t;
      start();
    }
    @Override
    public void run() {
      int oldCount = countMsgs;
      long sleeptime=30000;
      int nfail=0;
      while(true) {
        try {sleep(sleeptime);} catch(InterruptedException e) {prta("Interrupted");}
        if(thr.getCountMsgs() == oldCount) {
          nfail++;
          if(nfail > 2) {
            prta(tag+" "+getName()+" CheckProgress sees no progress, close the socket="+thr.getSocket()+" nmsgs="+oldCount);
            isConnected=false;
            reopen();
            sleeptime=60000;
            SendEvent.edgeSMEEvent("SSRNoProgress", "_"+tag+" "+Util.getIDText(), "StatusSocketReader");
          }
        }
        else nfail=0;
        oldCount = thr.getCountMsgs();
      }
    }
  }
  /** get the raw array of objects
   * @return The array of objects
   */
  public synchronized Object [] getObjectArray() {return msgs;}
  /**
   * Note we have to return a copy of our array because the user will likely sort it!
   * @return an array of objects we are keeping track of (of superclass StatusInfo)
   */
  public synchronized Object [] getObjects() { 
    if(nmsgs == 0 || msgs == null) return null;
    Object [] tmp = new Object[nmsgs];
    System.arraycopy(msgs, 0, tmp, 0, nmsgs);
    return tmp;
  }
    /**
   * Note we have to return a copy of our array because the user will likely sort it!
   * @param objs User array to put objects in
   * @return an array of objects we are keeping track of (of superclass StatusInfo)
   */
  public synchronized int getObjects(Object [] objs) { 
    if(nmsgs == 0 || msgs == null) return 0;
    System.arraycopy(msgs, 0, objs, 0, nmsgs);
    return nmsgs;
  }
  /** return number of unique items this class is updating since its inception
   *@return Number of items being tracked since startup*/
  public synchronized int length() { return nmsgs;}
  
  /** Unit test main
  * @param args the command line arguments
  */

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.init("edge.prop");
    Util.prt(Util.asctime());
    String host = Util.getProperty("StatusServer");
    if(host == null) host = "localhost";
    boolean dbg=false;
    int port=7992;
    String match="USDUG.*";
    for(int i=0; i<args.length; i++) {
      switch (args[i]) {
        case "-h":
          host = args[i+1];
          break;
        case "-p":
          port = Integer.parseInt(args[i+1]);
          break;
        case "-dbg":
          dbg=true;
          break;
        case "-re":
          match=args[i+1];
          break;
          
        case "-npass":
          
        case "-expected":
          int npass = Integer.parseInt(args[i+1]);
          DBConnectionThread  dbconnedge = DBConnectionThread.getThread("edge");
          while(dbconnedge == null) {
            try {
              dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"),"readonly","edge",
                      false,false,"edge", null);
              while(!DBConnectionThread.waitForConnection("edge")) Util.sleep(1000);
            }
            catch(InstantiationException e) {
              Util.prta("UdpChannel: InstantiationException on edge is impossible!");
              dbconnedge = DBConnectionThread.getThread("edge");
            }
          }
          StatusSocketReader t = new StatusSocketReader(ChannelStatus.class, host,port);
          Util.sleep(30000);
          StringBuilder sb = new StringBuilder(10000);
          ArrayList<String> chans = new ArrayList<>(2000);
          try {
            try (ResultSet rs = dbconnedge.executeQuery("SELECT channel FROM edge.channel WHERE expected != 0 order by channel")) {
              while(rs.next()) {
                chans.add(rs.getString(1));
              }
            }
          }
          catch(SQLException e) {
            Util.prt("SQLERR="+e);
            e.printStackTrace();
          }
          for(int pass=0; pass<npass; pass++) {
            int missing=0;
            Util.prta(pass+" nmsgs="+t.nmsgs);
            for (String ch : chans) {
              boolean found=false;
              for(int j=0; j<t.nmsgs; j++) {
                if( ((ChannelStatus) t.msgs[j]).getKey().trim().equals(ch)) {found=true; break;}
              }
              if(!found) {
                ch = (ch+"---").substring(0,12);
                sb.append(ch).append(" ").append((missing % 10 == 9?"\n":""));
                missing++;
              }
            }
            
            Util.prta("Missing="+missing+"/"+t.nmsgs+" "+Util.df22(missing*100./t.nmsgs)+"% "+sb.toString());
            if(sb.length() > 0) sb.delete(0, sb.length());
            sb.append("\n");
            Util.sleep(60000);
            
          }
          dbconnedge.close();
          Util.exit(101);
      }

    }
    StatusSocketReader t = new StatusSocketReader(ChannelStatus.class, host,port);
    for(;;) {
      Util.sleep(10000);

      Util.prt(" match="+match+" "+t.toString());
      
      if(dbg) {
        for(int i=0; i<t.nmsgs; i++) {
          Util.prta(i+"="+t.msgs[i].toString());
        }
      }
      if(match != null) {
        for(int i=0; i<t.nmsgs; i++) {
          ChannelStatus st = (ChannelStatus) t.msgs[i];
          if(st.getKey().matches(match)) Util.prt(i+"="+st.toString()+" s"+st.getCpu());
        }
      }
      Util.prta(t.toString());
    }
    //CmdListen t = new CmdListen(7997);
  }
}


