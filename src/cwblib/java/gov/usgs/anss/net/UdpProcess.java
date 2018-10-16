/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.net;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.ProcessStatus;
import gov.usgs.anss.util.StatusInfo;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.net.Socket;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
/*
* UdpListen.java
*
* Created on July 29, 2003, 4:15 PM
*/

/**
* This listens for UDP packets containing "StatusInfo" data.  The actual objects
 * represented by the UDP objects are implemented in a separate class which implement
 * the StatusInfo and Comparable interfaces.  This class implements a UDP listener 
 * which builds lists of status based on a Key and the Comparable (a list of unique
 * keys).  It only tracks all of the unique keys last instance so that that the
 * initial state can be sent via the ServerThread for new attachments.
 *
 * You create a new one of the Udpmsgs(Class, int port, ServerThread)
 * 
 * the "Class" is a data class implementing StatusInfo and Comparable (ex. ProcessStatus)
 * the port is the UDP port number to listen on
 * The ServerThread object is used to send any received UDP off to the list of msgs
 * that have requested the same data via a ServerSocket implemented in ServerThread. 
 * This ServerThread contains all the details to pass the information to connecting
 * clients.
 *
 *The list of senders is implemented via the Collections.synchronizedList(new ArrayList) 
 * so that the iterators on the list can be synchronized.  This is discuss briefly in 
 *Eckel pg 566.  You must wrap all uses of iterators in a "synchronized (senders)" to 
 *insure the iterators access is properly synchronized.  When you do not do this you can get
 * ConcurrentModificationException in the iterator manipulations.
 *

* @author  ketchum
*/

public class UdpProcess extends Thread{
  private DatagramSocket d;
  private final List<Sender> senders = Collections.synchronizedList(new ArrayList<Sender>(100));// 100 computers sending process info
  private StatusInfo [] msgs;
  private final Integer msgsMutex = Util.nextMutex();
  private long lastStatus;
  private ServerThread server;
  private int port;                         // Port we are listening too!
  private int nmsgs;
  private Class objClass;
  private Constructor emptyConstructor;
  private Constructor bufferConstructor;
  private Constructor keyConstructor;
  private int msgLength;
  private int state;
  private DebugTimer dbgtime;
  private int totmsgs;
  private boolean channelStatus;
  private String tag;
  private EdgeThread par;
  private final StringBuilder st = new StringBuilder(1000);
  private final StringBuilder sb = new StringBuilder(2000);
  public StringBuilder getServerWriteStatus() {return server.getWriteState();}
  public int getWhoHas() {return server.getWhoHas();}
  public int getNumberOfSenders() {if(senders == null) return 0; else return senders.size();}
  public int getState2() {return state;}
  public int getNumberOfUDP() {return nmsgs;}
  public int getNumberOfMessages() {return totmsgs;}
  public void setParent(EdgeThread parent) {par = parent;}
  private void prt(String a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prt(StringBuilder a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prta(String a) {if(par == null) Util.prta(a); else par.prta(a);}
  private void prta(StringBuilder a) {if(par == null) Util.prta(a); else par.prta(a);}
   
  
  /** Creates a new instance of UdpProcess running on port por
   * @param cl The class that will be received UDP
   * @param pt The UDP port
   * @param s The server thread to use to process this data
   * @param parent
   */
  public UdpProcess(Class cl, int pt, ServerThread s, EdgeThread parent) {
    par=parent;
    doIt(cl,pt,s,true);
  }
  /**
   * 
   * @param cl The class that will be received UDP
   * @param pt The UDP port
   * @param s The server thread to use to process this data
   */
  public UdpProcess(Class cl, int pt, ServerThread s){ doIt(cl,pt,s,true);}
  /**
   * @param cl The class that will be received UDP
   * @param pt The UDP port
   * @param s The server thread to use to process this data
   * @param start It true the thread is started, if false this is skipped
   * @param parent The parent for logging
   */
  public UdpProcess(Class cl, int pt, ServerThread s, boolean start, EdgeThread parent){ 
    par=parent;
    doIt(cl,pt,s,start);
  }
  /**
   * 
    * @param cl The class that will be received UDP
   * @param pt The UDP port
   * @param s The server thread to use to process this data
   * @param start It true the thread is started, if false this is skipped
   */
  public UdpProcess(Class cl, int pt, ServerThread s, boolean start){ doIt(cl,pt,s,start);}
  private void doIt(Class cl, int pt, ServerThread s, boolean start) {
    port=pt;
    server = s;
    objClass = cl;
    tag="["+objClass.getName()+":"+pt+"]";
    if(cl.getName().contains("ChannelStatus")) channelStatus=true;
    s.setUdpProcess(this);
    try { 
      dbgtime=new DebugTimer();
      
      Class [] em = new Class[0];
      emptyConstructor = objClass.getConstructor(em);
      Method getLength = objClass.getMethod("getMaxLength", em);
      Integer tmp = (Integer) getLength.invoke(null, (Object []) null);
      msgLength = tmp;
      prt(tag+"Max Length discovered="+msgLength+" UDPProc on "+server+" port="+port);
      byte [] bb = new byte [msgLength];
      em = new Class[1];
      em[0]= bb.getClass();     // An array of bytes
      bufferConstructor = objClass.getConstructor(em);
      
      String s2 = "Tmp";
      em[0]= s2.getClass();
      keyConstructor = objClass.getConstructor(em);
      prt(tag+"KeyConstructor ="+keyConstructor+" for "+objClass);
      msgs = new StatusInfo[20];          // Number of msgs we can track
      nmsgs=0;
      Object [] emptyArgs= new Object[0];
      for(int i=0; i<20; i++) {
        msgs[i]= (StatusInfo) emptyConstructor.newInstance(emptyArgs);
        msgs[i].setParent(par);
      }//new ProcessStatus();
    } 
    catch (IllegalAccessException e) {
      prta(tag+"Illegal Access exception="+e.getMessage());
    } catch (InvocationTargetException e) {
      prta(tag+"Invocation Target Exception "+e.getMessage());
    } catch (NoSuchMethodException e) {
      prt(tag+"Nosuch method constructor found for class="+cl);
      return;
    } catch (SecurityException e2) {
      prt(tag+"Security Exception getting constructors!!!!");
      e2.printStackTrace();
    }
    catch (InstantiationException e) {
      prt(tag+"instantiationexception ");
      Util.exit(0);
    }

    if(start) start();
  }
  public void doStart() { start();}
  @Override
  public void run() {
   boolean dbg=false;
    byte [] buf= new byte[100];
    Object [] emptyArgs= new Object[0];
    StringBuilder runsb = new StringBuilder(10);
    state=1;
    while(true) {
      try {
        //server = s;
        prta(Util.clear(runsb).append(tag).append(" UdpProcess: Open Port=").append(port));
        d = new DatagramSocket(port);
        lastStatus = System.currentTimeMillis();
        break;
      }
      catch (SocketException e) {
       if(e.getMessage().equals("Address already in use")) {
            try {
              prt(tag+"Address in use - try again.");
              Thread.sleep(2000);
            }
            catch (InterruptedException E) {
          }
        }
      }
    }
    DatagramPacket p = new DatagramPacket(buf, msgLength);
    prta(tag+"run init code");
    state=2;
    StatusInfo ps = null;     // this is a object for us to process
    long now;
    boolean ok=true;
    byte byt;
    String key="";
    // This loop receive UDP packets 
    while(true) {
      try {
       state=3;
        d.receive(p);
        totmsgs++;
        state=4;
        if(channelStatus) {
          ok=true;
          byt=p.getData()[38+7];
          if(p.getData()[38+9] != 'Z') ok = false;
          else if(p.getData()[38+8] != 'H') ok = false;
          else if( byt != 'B' && byt != 'H' && byt != 'S' && byt != 'E') ok=false; 
        }
        state=44;
        server.writeAll(p.getData(), msgLength, ok);   // send it to all requestors
        state=45;
        if(dbg) prta(Util.clear(runsb).append(tag).append(" UdpProcess: Rcv adr=").append(p.getSocketAddress()).
                append(" len=").append(p.getLength()));
        if(p.getLength() >
                msgLength) prta(Util.clear(runsb).append(tag).append("Datagram wrong size=").append(msgLength).
                        append("!=").append(p.getLength()).append(" rcv adr=").append(p.getSocketAddress()));
        now = System.currentTimeMillis();
        if(ps == null) {        // we do not have an object to work with
          Object [] args = new Object[1];
          args[0]=p.getData();
          ps = (StatusInfo) bufferConstructor.newInstance(args);// new ProcessStatus(p);
          ((StatusInfo) ps).setParent(par);
        }
        else
          ((StatusInfo)ps).reload(p.getData());         // We do, just load it instead of creating  a new one
        ((StatusInfo) ps).createRecord();               // This would create a MySql if defined
        state=5;
        if(doProcess(ps)) ps = null;        // We put that one away, need to create another next time

        // Build a list of computers that are sending to us for statistics purposes
        boolean found=false;
        synchronized(senders) {
          for (Sender s : senders) {
            //prt("look "+p.getAddress().toString()+" = "+ s.getInetAddress().toString());
            if(p.getAddress().equals( s.getInetAddress())) {
              s.setLastTime( now);
              found=true;
              break;
            }
          }    
        }
        state=8;
        // Send to the client computers
        if( !found) {
          state=9;
          prta(Util.clear(runsb).append(tag).append(" UdpProcess: Add data source ").append(p.getAddress())); 
          senders.add( new Sender(p.getAddress()));
        }

        // Is it time for status yet
        if( Math.abs(now - lastStatus) > 300000) {
          state=10;
          if(st.length() > 0) st.delete(0,st.length());
          st.append(Util.asctime()).append(tag).append(" Size=").append(nmsgs).append(" Udp From ");
          synchronized(senders) {
            Iterator it = senders.iterator();
            int i=0;
            while(it.hasNext()) {
              Sender sd = (Sender) it.next(); 
              InetAddress inet = sd.getInetAddress();
              st.append(" ").append(inet.toString()).append(" ").append(sd.getNumberOfPackets());
              sd.resetNumberOfPackets();
              if( (i % 8) == 7) st.append("\n");
              i++;
            }
          }
          prt(st.toString());
          lastStatus = now;
        }
        state=11;
      }
      catch (IOException e) {
       prta(tag+"receive through IO execption");
      }
      catch (InvocationTargetException e) {
          prta(tag+"InvocationTargetException : This is impossible2b!");
          Util.exit(0);
      } catch (IllegalAccessException E) {
          prta(tag+"Illegal Access exception.  Impossible!");
          Util.exit(0);
      } catch(InstantiationException e2) {
          prta(tag+"instantiaionException is impossible!");
          Util.exit(0);
      }
      catch(RuntimeException e) {
        if(e.toString().contains("OutOfMemory")) {
          prta(tag+"RuntimeException UdpProcess OutOfMemory - exit  e="+e);
          Util.exit(1);
        }
        else prta(tag+"RuntimeException UdpProcess continue.  e="+e);

      }
    }
    //prt("Exiting UdpProcess run()!! should never happen!****\n");
  }
  
  public boolean doProcess(StatusInfo ps) {
    boolean dbg=false;
    boolean created=false;
    synchronized (msgsMutex) {
      int ins = Arrays.binarySearch(msgs, ps);
      state=6;
      if(ins >= 0) {
          ((StatusInfo) msgs[ins]).update(ps);
          if(dbg) prt(tag+"Update rec="+ins+" "+msgs[ins]);
      } else {
          nmsgs=nmsgs+1;
          created=true;
          if(nmsgs > msgs.length) {
            prta(tag+"expand msgs by 100 len="+msgs.length+" nmsgs="+nmsgs);
            StatusInfo [] tmp = new StatusInfo[msgs.length+100];
            System.arraycopy(msgs, 0, tmp, 0, msgs.length);
            try {
              Object [] emptyArgs= new Object[0];
              for(int i=0; i<100; i++) {
                tmp[msgs.length+i] = (StatusInfo) emptyConstructor.newInstance(emptyArgs);
                tmp[msgs.length+i].setParent(par);
              }
              msgs = tmp;
            }
            catch (InvocationTargetException e) {
                prt(tag+"InvocationTargetException : This is impossible2b!");
                Util.exit(0);
            } catch (IllegalAccessException E) {
                prt(tag+"Illegal Access exception.  Impossible!");
                Util.exit(0);
            } catch(InstantiationException e2) {
                prt(tag+"instantiaionException is impossible!");
                Util.exit(0);
            }
              
          }
          int insertAt = -(ins +1);
          if(dbg) prt(tag+"Ins "+insertAt+" ps="+ps.toString());
          System.arraycopy(msgs, insertAt, msgs, insertAt+1, msgs.length-insertAt-1);
          msgs[insertAt]=ps;
      }
    }       // synchronized (msgsMutex) 
    state=7;

    if(dbg) {
        if(sb.length() > 0) sb.delete(0,sb.length());
        for(int i=0; i<msgs.length; i++) {
            sb.append(i).append("=").append(((StatusInfo)msgs[i]).getKey());
            if(i % 4 == 3) {
                prt(sb.toString());
                sb.delete(0,sb.length());
            }
        }
    }
    return created;
  }
  
  
  public void forceKey(String s) {
    Object [] args = new Object[1];
    Object [] emptyArgs = new Object[0];
    args[0] = s;
    while(msgs == null){ 
      try {Thread.sleep(2000);} catch(InterruptedException e) {}
    }

    try {
      //prt("ForceKey="+args[0]);
      StatusInfo ps = (StatusInfo) keyConstructor.newInstance(args);
      synchronized(msgsMutex) {
        int ins = Arrays.binarySearch(msgs, ps);
        if(ins >= 0) {
            ((StatusInfo) msgs[ins]).update(ps);
            prt(tag+"Force key with update should not happend key="+ins+" "+s);
        } else {
            nmsgs=nmsgs+1;
            if(nmsgs > msgs.length) {
              prta(tag+"expand msgs by 100 len="+msgs.length+" nmsgs="+nmsgs);
              StatusInfo [] tmp = new StatusInfo[msgs.length+100];
              System.arraycopy(msgs, 0, tmp, 0, msgs.length);
              for(int i=0; i<100; i++) {
                tmp[msgs.length+i] = (StatusInfo) emptyConstructor.newInstance(emptyArgs);
                tmp[msgs.length+i].setParent(par);
              }
              msgs = tmp;
            }
            int insertAt = -(ins +1);
            //prt("Force Insert record at "+insertAt+" ps="+ps.toString());
            System.arraycopy(msgs, insertAt, msgs, insertAt+1, msgs.length-insertAt-1);
            msgs[insertAt]=ps;
        }
      }
    } catch(InstantiationException e2) {
        prt(tag+"ForceKey: instantiaionException in keyForce is impossible!");
        Util.exit(0);
    }    
    catch (InvocationTargetException e) {
        prt(tag+"ForceKey : InvocationTargetException : This is impossible3b!"+(e == null?"null":e.getMessage()));
        if(e != null) e.printStackTrace();
        Util.exit(0);
    } catch (IllegalAccessException E) {
        prt(tag+"ForceKeyIllegal Access exception.  Impossible!");
        Util.exit(0);
    }
  }
  
  public void startupDump(Socket out, boolean outside) throws IOException {
   byte [] b ;
   if(out == null) {prt(tag+" startupDump out is null!"); return;}
   else prta(tag+out.getInetAddress()+" StartupDump out="+out+" nmsgs="+nmsgs+"/"+msgs.length+
           " outside="+outside+" channelStatus="+channelStatus+" ssize="+out.getSendBufferSize());
   int nout=0;
   int nrec=0;
   synchronized(msgsMutex) {
    for (int i=0; i<nmsgs; i++) {
      if(i >= msgs.length) continue;
      b = ((StatusInfo) msgs[i]).makeBuf();
      if(channelStatus  && outside) {
         boolean ok=true;
         byte byt=b[38+7];
         if(b[38+9] != 'Z') ok = false;
         else if(b[38+8] != 'H') ok = false;
         else if( byt != 'B' && byt != 'H' && byt != 'S' && byt != 'E') ok=false; 
         if(!ok) continue;
      }


      //prt(tag+i+" Startup out="+b.length);
      //prta(tag+i+" startup "+b.length+" "+(msgs[i]).toString());
      out.getOutputStream().write(b, 0, b.length);
      nout += b.length;
      nrec++;
    }
   }
   prta(tag+out.getInetAddress()+" StartupDump out="+out+" completed #bytes="+nout+" nrec="+nrec+" msgs="+nmsgs);
  }
  
  /**  This private class keeps track of how many different IP addresses are sending
   *  information and when the last time information was received.  It is used for the
   * statistics keeping
    */
  private class Sender {
   InetAddress addr;
   long lastTime;
   int numPackets;
   public Sender (InetAddress a) {
     addr = a;
     lastTime = System.currentTimeMillis();
     numPackets=0;
   }
   public InetAddress getInetAddress() { return addr;}
   public long getLastTime() { return lastTime;}
   public int getNumberOfPackets() {return numPackets;}
   public void setLastTime( long d) { numPackets++; lastTime = d;}
   public void incNumberOfPackets() {numPackets++;}
   public void resetNumberOfPackets() { numPackets = 0;}
   
  }
  
  
  private class DebugTimer extends Thread {
   public DebugTimer() {
     start();
   }
    @Override
   public void run() {
     StringBuilder runsb=new StringBuilder(100);
     while (true) {
       try {
         sleep(300000);
         prta(Util.clear(runsb).append(Util.ascdate().substring(5)).append(tag).append(" state=").append(state).
                 append(" totmsg=").append(totmsgs).append(" nmsgs=").append(nmsgs).
                 append(" nsenders=").append(senders.size()));
       }
       catch (InterruptedException e) {
         prt(tag+"DebugTimer interupted");
       }
     }
   }
  }
  /**
  * @param args the command line arguments
  */

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.prt(Util.asctime());
    ServerThread server = new ServerThread(AnssPorts.PROCESS_SERVER_PORT, false);
    UdpProcess t = new UdpProcess(ProcessStatus.class, AnssPorts.PROCESS_UDP_PORT, server, true);
    //CmdListen t = new CmdListen(7997);
  }
}

