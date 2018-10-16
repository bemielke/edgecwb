/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.net;
import gov.usgs.anss.util.ClientSocket;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/** This class listens to a port for socket connections in a Thread.  
 For each incoming socket connection found
 *a "ClientSocket" object is created.  The
 *list of clients is maintained internally so that methods can effect all of the
 *clients.  The inner class WatchClient class sets up a thread to detected hung writes
 *in a ClientSocket.  
 *
 *The list of Clients is implemented via the Collections.synchronizedList(new ArrayList) 
 * so that the iterators on the list can be synchronized.  This is discuss briefly in 
 *Eckel pg 566.  You must wrap all uses of iterators in a "synchronized (clients)" to 
 *insure the iterators access is properly synchronized.  When you do not do this you can get
 * ConcurrentModificationException in the iterator manipulations.
 *
 *Normally you must register a UdpProcess with this object so that the UdpProcess
 *can be notified 
 *
 * ServerThread.java
 *
 * @author  ketchum
 */
public class ServerThread extends Thread
{
  private ServerSocket ss;                // the socket we are "listening" for new connections
  private final List<ClientSocket> clients = Collections.synchronizedList(new LinkedList<ClientSocket>());           // list of clients which have attached (ClientSockets)
                                  // note this is in a Collections.synchronizedList() to support
                                  // synchronized iterators
  private long lastStatus;
  private UdpProcess udp;         // The UDP receive process object, so we can notify it
  private WatchClients wc;        // This is a watchdog on the write threads of the ClientSockets
  private boolean gomberg;         // if true, then this is handling a gomberg socket
  private int maxBuffers=1000000;
  private EdgeThread par;
  private int whohas;
  private StringBuilder st = new StringBuilder(200);
  public void setParent(EdgeThread parent) {par = parent; if(udp != null) udp.setParent(parent);}
  public void setMaxBuffers(int i) {maxBuffers=i;}
  public int getWhoHas() {return whohas;}
  private void prt(String a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prt(StringBuilder a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prta(String a) {if(par == null) Util.prta(a); else par.prta(a);}
  private void prta(StringBuilder a) {if(par == null) Util.prta(a); else par.prta(a);}
  /** Creates a new instance of ServerThread 
   * @param port The int with the port number this ServerThread is to listen on
   * @param gb Boolean indicating whether incoming data in Gomberg for is expected 
   */
  public ServerThread(int port, boolean gb)  {
    this(port,gb,null);
  }
  /** Creates a new instance of ServerThread 
   * @param port The int with the port number this ServerThread is to listen on
   * @param gb Boolean indicating whether incoming data in Gomberg for is expected 
   * @param parent Logging edgethread
   */
  public ServerThread(int port, boolean gb, EdgeThread parent)
  { par = parent;
    try
    {
      gomberg=gb;
      ss = new ServerSocket(port);            // Create the listener
      lastStatus = System.currentTimeMillis();   // Set initial time
      wc = new WatchClients();   //(clients);         // Turn on the watcher of clients writes
      start();
    }
    catch(IOException e)
    {
      Util.IOErrorPrint(e,"ServerThread : Cannot set up socket server on port "+port);
      Util.exit(10);
    }
  }
  /** This Thread does accepts() on the port an creates a new ClientSocket 
   *inner class object to track its progress (basically hold information about
   *the client and detect writes that are hung via a timeout Thread
   */
  @Override
  public void run()
  {
    prt(Util.asctime()+" ServerThread : start accept loop.  I am "+
    ss.getInetAddress()+"-"+ss.getLocalSocketAddress()+"|"+ss);
    StringBuilder sb = new StringBuilder();
    while (true)
    {
      prta(" ServerThread: call accept()");
      try
      {
        Socket s = ss.accept();
        ClientSocket c;
        whohas=1;
        prta(Util.clear(sb).append(" ServerThread: new socket=").append(s).append(" at client=").append(clients.size()));
        c = new ClientSocket(s, gomberg, maxBuffers);
        c.setParent(par);
        whohas=0;
        prt(Util.clear(sb).append("ServerThread: out for startup is ").append(c.getSocket()));
        try {
          udp.startupDump(c.getSocket(), c.isOutside());       // get all startup data from the UDP server
          synchronized(clients) {
            clients.add(c);
          }
        }
        catch(IOException e) {
          prta("ServerThread: got Error dumping to new socket c="+c+" e="+e.getMessage());
          c.close();
        }

      }
      catch (IOException e)
      {
        Util.IOErrorPrint(e,"ServerThread: accept gave IOException!");
        Util.exit(0);
      }
    }
  }
  
  /** Register the Udp process with us so we can request startup data.
   *@param u The UdpProcess to associate with this server */
  public void setUdpProcess(UdpProcess u) { udp = u; udp.setParent(par);}
  private final StringBuilder writeState = new StringBuilder(100);
  public StringBuilder getWriteState() {return writeState;}
  /** write the raw bytes to all of the ClientSockets registered with us to date.  This
   *routine also periodically outputs status information about the levels of output
   *to the clients.
   *@param buf Raw data buffer to write
   *@param len The length of the raw data buffer
   * @param okoutside*/
  public void writeAll(byte [] buf, int len, boolean okoutside)
  {
    ClientSocket c=null;
    Iterator it;
    writeState.delete(0,writeState.length());
    writeState.append("Sync try ").append(whohas).append("\n");
    whohas=2;
    synchronized (clients) {
      writeState.append("Sync ok\n");
      it = clients.iterator();
      //prt(Util.asctime()+" ServerThread: writeAll() len="+len);
      //StringBuffer s = new StringBuffer(300);
      //s.append(Util.asctime()+" Server:send l="+len+" # cl="+clients.size());
      while ( it.hasNext())
      {
        try
        { 
          c = (ClientSocket) it.next(); 
          if(c == null) continue;
          if(!c.isOutside() || okoutside) {
            writeState.append(c.toStringSB()).append(" ok=").append(okoutside).append(" len=").append(len).append("\n");
            //s.append(" "+c.toString());
            //prt(Util.asctime()+" ServerThread : Write gomberg to "+c+"len="+len);
            if(gomberg) c.sendBufAsGomberg(buf, len);
            else c.write(buf,len);
          }
        }
        catch (IOException e) {
         if(e.getMessage().equals("Socket is closed.")) prta("Client closed up.  Remove..."+c);
          else if(e.getMessage().contains("Exceeded max buffering")) {
            prta(c+" "+e.getMessage()+" closing socket");
          }
          else Util.IOErrorPrint(e, Util.asctime()+" ServerThread :Client for "+c+" write error.  Close up.");
          if(c != null) c.close();
          it.remove();
        }
        catch(NoSuchElementException e)
        {
          prt(Util.asctime()+" ServerThread: No such element not expected.");
          e.printStackTrace();
        }
      }
    }             // synchronize clients
    whohas=0;
    //prt(s.toString());
    writeState.append("Done 1!\n");
    // Time for status?
    long now = System.currentTimeMillis();
    whohas=3;
    synchronized (clients) {
      writeState.append("Sync 2 done\n");
      it = clients.iterator();
      if( Math.abs(now - lastStatus) > 300000)
      {
        lastStatus = now;
        if(st.length() > 0) st.delete(0,st.length());
        //st.append(Util.asctime()+" Clients  ");
        for(int i=0; i<clients.size(); i++)
        {
          c = (ClientSocket) it.next();
          st.append(i % 3 == 0? "Clients:":"").append(c.toStringSB()).append(i % 3 == 2?"\n":" ");

        }
        if(clients.size()-1 % 3 != 2) st.append("\n");
        prt(st);
      }
    }
    whohas=0;
    writeState.append("return\n");
  }
  
  /**
   * this internal class is used to set up a thread which monitors the write
   * threads of the clients.  If they become stuck in a write state, this thread
   * will close the socket and delete the client.
   */
  private class WatchClients extends Thread
  { long lastStatus;
    //List clients;
    WatchClients()    //(List cl)
    {
      //clients = cl;
      start();
    }
    /** every 10 seconds look at each of the clients and see if they have a write
     *pending and whether it has been in this state at least 30 seconds.  If it has
     *timed out, close the client and remove it from the list of clients.
     */
    @Override
    public void run()
    { ClientSocket c;
      lastStatus=System.currentTimeMillis();
      StringBuilder sb = new StringBuilder(10);
      while(true)
      {
        try
        {
          sleep(10000);
          long now=System.currentTimeMillis();
          whohas=5;
          synchronized (clients) {
            //prt(Util.asctime()+" WatchClients check.");
            Iterator it = clients.iterator();
            while ( it.hasNext())
            {
              c = (ClientSocket) it.next();
              //if(now - lastStatus > 600000) prta("WC:"+c);
              if(c.isClosed()) {it.remove(); prta(Util.clear(sb).append("Client closed remove it ").append(c.toStringSB()));}
              else if(c.getWriteActive() && c.getWriteWaitMS() > 30000)
              {
                prta(Util.clear(sb).append("Timeout write on ").append(c.toStringSB()));
                c.close();
                it.remove();
              }
            }
            whohas=0;
            if(now - lastStatus > 600000) lastStatus=now;
          }         // synchronized(clients)
        }catch (InterruptedException e)
        {
          prt("WatchCLients : interrupted exception.");
        }
      }
    }
  }
  /** Unit test main()
   *@param args Unused command line args*/ 
  public static void main(String[] args)
  {
    Util.prt(Util.asctime());
    ServerThread server = new ServerThread(7990, false);
  }
}