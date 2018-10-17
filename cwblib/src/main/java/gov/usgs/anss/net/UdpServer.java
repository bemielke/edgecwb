/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.net;
/*
* UdpServer.java
*
 *The list of Clients is implemented via the Collections.synchronizedList(new ArrayList) 
 * so that the iterators on the list can be synchronized.  This is discuss briefly in 
 *Eckel pg 566.  You must wrap all uses of iterators in a "synchronized (clients)" to 
 *insure the iterators access is properly synchronized.  When you do not do this you can get
 * ConcurrentModificationException in the iterator manipulations.
 *

* Created on July 29, 2003, 5:50 PM
*/

/**
*
* @author  ketchum
*/
import java.net.*;
import java.io.*;
import java.util.*;
import gov.usgs.anss.util.*;

public class UdpServer extends Thread {
  private ServerSocket ss;
  private final List<Client> clients = Collections.synchronizedList(new LinkedList<Client>());
  private GregorianCalendar lastStatus;
  /** Creates a new instance of UdpServer
   * @param port */
  public UdpServer(int port) {
    try {
      ss = new ServerSocket(port);
      lastStatus = new GregorianCalendar();
      start();
    }
    catch(IOException e) {
      Util.prt("Cannot set up socket server on port "+port);
      e.printStackTrace();
      Util.exit(10);
    }
  }
  @Override
  public void run() {
    Util.prt(Util.asctime()+" UdpServer : start accept loop.  I am "+
      ss.getInetAddress()+"-"+ss.getLocalSocketAddress()+"|"+ss);
    while (true) {
      Util.prt(Util.asctime()+" UdpServer: call accept()");
      try {
        Socket s = ss.accept();
        synchronized (clients) {
          Util.prt(Util.asctime()+" UdpServer: new socket="+s+" at client="+clients.size());
          Client c = new Client(s);
          clients.add(c); 
        }
      }
      catch (IOException e) {
        Util.prt("UdpServer: accept gave IOException!");
        e.printStackTrace();
        Util.exit(0);
      }
    }
  }
  
  public synchronized void sendStatus(byte [] buf, int len) {
    Client c;
    synchronized(clients) {
      Iterator it = clients.iterator();
      //Util.prt(Util.asctime()+" UdpServer: sendStatus() len="+len);
      //StringBuffer s = new StringBuffer(300);
      //s.append(Util.asctime()+" Server:send l="+len+" # cl="+clients.size());
      while ( it.hasNext()) {
        try {
          c = (Client) it.next();
          //s.append(" "+c.toString());
          //Util.prt(Util.asctime()+" UdpServer : Write gomberg to "+c+"len="+len);
          c.sendBufAsGomberg(buf, len);
        }
        catch (IOException e) {
          Util.prt(Util.asctime()+" UdpServer :Client for "+it+" write error.  Close up.");
          it.remove();
        }
        catch(NoSuchElementException e) {
          Util.prt(Util.asctime()+" UdpServer: No such element not expected.");
          e.printStackTrace();
        }
      }
    }
    //Util.prt(s.toString());
    
    // Time for status?
    GregorianCalendar now = new GregorianCalendar();
    synchronized (clients) {
      Iterator it = clients.iterator();
      if( (now.getTimeInMillis() - lastStatus.getTimeInMillis()) > 300000) {
        lastStatus = now;
        StringBuilder st = new StringBuilder(200);
        st.append(Util.asctime()).append(" Clients  ");
        while( it.hasNext()){
          c = (Client) it.next();
          st.append(c.toString()).append(" ");
        }
        Util.prt(st.toString());
      }
    }
  }
  
  private class Client {
    Socket s;
    OutputStream out;
    int pseq;
    byte [] buf;
    @Override
    public String toString() { return s.getInetAddress().toString()+" sq="+ (pseq & 0xff);}
   /*   String ss = s.getInetAddress().toString();
      int i = ss.indexOf('.');
      i = ss.indexOf('.', i+1);
      return ""+ss.substring(i+1)+" sq="+(pseq&0xff);
    }*/
    public Client(Socket st) {
      s = st;
      buf = new byte[2048];
      pseq=1;
      try {
        out = s.getOutputStream();
      } catch(IOException e) {
        Util.prt("UdpServer: Create client filed to get OutputStream");
        e.printStackTrace();
      }
    }
    public void sendBufAsGomberg(byte [] buf, int len) throws IOException {
      GregorianCalendar d = new GregorianCalendar(); // Get system date/time
      NsnTime tc = new NsnTime(d.get(Calendar.YEAR), d.get(Calendar.DAY_OF_YEAR),
      d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE), d.get(Calendar.SECOND),
      d.get(Calendar.MILLISECOND), 0);
      GombergPacket gb = new GombergPacket( (short) (len+14), (byte) -2, (byte) 0,
      (byte) 126, (byte) (pseq++ & 0xff), tc, buf);
      byte [] b = gb.getAsBuf();
      try {
        out.write(b, 0, len+14);
      }
      catch (IOException e) {
        try {
          s.close();
          throw e;
        }
        catch(IOException e2) {
          Util.prt("UdpServer: Client IOException while closing bad socket");
          throw e2;
        }
      }
    }
    
  } // end of class Client
}


