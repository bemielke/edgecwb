/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.net;
/*
* CmdListen.java
 *
 * This class listens to a port and accepts socket connections.  Each socket connection
 * creates in interal client object which does actual I/O.  The user can then send 
 * commands via sendCmd or sendCmdAll().  So, this is used to make user connections
 * to a server program like RTSServer, and send the bytes making the command.  No data is ever 
 * received on a socket maintained by this.  It listens and writes only.
 *
 * It is used by RTSServer to listen for RTS connections and maintain them.  RTSUtilityCommand()
 *then uses an object of this class to send data to the RTSes.  
 *
 *The list of Clients is implemented via the Collections.synchronizedList(new ArrayList) 
 * so that the iterators on the list can be synchronized.  This is discuss briefly in 
 *Eckel pg 566.  You must wrap all uses of iterators in a "synchronized (clients)" to 
 *insure the iterators access is properly synchronized.  When you do not do this you can get
 * ConcurrentModificationException in the iterator manipulations.
 *
* I think this was used to send messages to VMS and is now obsolete.

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

public class CmdListen extends Thread {
  private ServerSocket ss;
  private final TreeMap<String, Client> clients =new TreeMap<String,Client>();
  private GregorianCalendar lastStatus;
  private DatagramSocket udp;
  private int port;
  /** Creates a new instance of CmdListen
   * @param pt The port to listen on*/
  public CmdListen(int pt) {
    try {
      port=pt;
      ss = new ServerSocket(pt);
      lastStatus = new GregorianCalendar();
      udp = new DatagramSocket();
      //Util.prt(Util.asctime()+" CmdListen("+port+"): open on port "+port);
      start();
    }
    catch(IOException e) {
      Util.prt("CmdListen("+port+"): Cannot set up socket server on port "+port);
      e.printStackTrace();
      Util.exit(10);
    }
  }
  @Override
  public void run() {
    Util.prta("CmdListen("+port+"):  start accept loop.  I am "+
      ss.getInetAddress()+"-"+ss.getLocalSocketAddress()+"|"+ss);
    while (true) {
      Util.prta(" CmdListen("+port+"): call accept()");
      try {

        Socket s = ss.accept();
        boolean changed=true;
        String adr = s.getInetAddress().getHostAddress();
        synchronized(clients) {
          Client cc = clients.get(adr);
          if(cc != null) cc.close();
          Util.prta(" CmdListen("+port+"): new socket="+s+" at client="+clients.size());
          Client c = new Client(s);
          clients.put(adr, c);
        }           // synchronized clients()

      }
      catch (IOException e) {
        Util.prt("CmdListen("+port+"): accept gave IOException!");
        e.printStackTrace();
        Util.exit(0);
      }
    }
  }
  public OutputStream getOutputStream(String ipadr) {
    synchronized (clients) {
      try {
        Client c=clients.get(ipadr);
        if(c == null ) return null;
        return c.getOutputStream();
      } 
      catch(IOException e) {
        return null;
      }
    }
  }
  
  public  InputStream getInputStream(String ipadr) {
    synchronized (clients) {
      try {
        Client c = clients.get(ipadr.trim());
        if(c == null) return null;
        return c.getInputStream();
      } 
        catch(IOException e) {
        return null;
      }
    }     // synchronized (clients)
  }
  public synchronized String sendCmd(String ipadr, byte [] buf, int len) {
    boolean dbg=false;
    boolean found=false;
    String ret = "no IP from "+ipadr.trim();
    if( ipadr.startsWith("1.") || ipadr.startsWith("001.")) {
      return "Place Holder address skip "+ipadr;     // place holder address.
    }
    synchronized (clients) {
      Client c = clients.get(ipadr.trim());
      if(c != null && !c.isClosed()) {
        try {
          if(dbg) 
            Util.prta("CmdListen("+port+"): Try to find ip match="+ipadr+" "+c.getIpadr()+
            " t="+c.getIpadr().trim().equals(ipadr.trim()));
          if(dbg)
            Util.prta("CmdListen("+port+"): match found! len="+len+" "+buf.length+" "+
            buf[0]+" "+buf[1]+" "+buf[2]+" "+buf[3]);
          c.send(buf, len);
          ret = "O.K.";
        }
        catch(IOException e) {
          Util.prt("CmdListen("+port+"): Could not write to client="+c);
          ret = "Write failed to IP="+ipadr;
        }
      }
      //else {
      // We did not find a socket (client) to the given site.  Try UDP as a backup
        try {
          InetAddress address = InetAddress.getByName(ipadr.trim());
          DatagramPacket dp = new DatagramPacket(buf, 0, len, address, 7999);
          udp.send(dp);
          ret = ret + " -UDP done.";
        }
        catch (UnknownHostException e) {
          Util.prta("UDP backup try to "+ipadr+"| gave UnknownHostException-"+e.getMessage());
        }
        catch (IOException e) {
          Util.prta("UDP backup try to "+ipadr+"| gave IOException-"+e.getMessage());
        }
      //}
    }
    return ret;
  }
    
  
  public synchronized void sendCmdAll(byte [] buf, int len) {
    Client c;
    synchronized (clients) {
      Iterator it = clients.values().iterator();
      //Util.prt(Util.asctime()+" CmdListen("+port+"): sendStatus() len="+len);
      //StringBuffer s = new StringBuffer(300);
      //s.append(Util.asctime()+" Server:send l="+len+" # cl="+clients.size());
      while ( it.hasNext()) {
        try {
          c = (Client) it.next();
          //s.append(" "+c.toString());
          //Util.prt(Util.asctime()+" CmdListen("+port+"): Write gomberg to "+c+"len="+len);
          c.sendBufAsGomberg(buf, len);
        }
        catch (IOException e) {
          Util.prt(Util.asctime()+" CmdListen("+port+"):Client for "+it+" write error.  Close up.");
          it.remove();
        }
        catch(NoSuchElementException e) {
          Util.prt(Util.asctime()+" CmdListen("+port+"): No such element not expected.");
          e.printStackTrace();
        }
      }
    }         // synchronized clients
    //Util.prt(s.toString());
    
    // Time for status?
    GregorianCalendar now = new GregorianCalendar();
    synchronized(clients) {
      Iterator it = clients.values().iterator();
      if( (now.getTimeInMillis() - lastStatus.getTimeInMillis()) > 1800000) {
        lastStatus = now;
        StringBuilder st = new StringBuilder(200);
        st.append(Util.asctime()).append(" Clients  ");
        for(int i=0; i<clients.size(); i++) {
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
    String tag;
    @Override
    public String toString() { return tag+" sq="+ (pseq & 0xff);}
    public OutputStream getOutputStream() throws IOException {return s.getOutputStream();}
    public InputStream getInputStream() throws IOException {return s.getInputStream();}

    public Client(Socket st) {
      s = st;
      buf = new byte[2048];
      pseq=1;
      tag = "CmdLClient:["+s.getInetAddress().toString().substring(1)+"/"+s.getPort()+"]:";
      try {
        out = s.getOutputStream();
      } catch(IOException e) {
        Util.prt(tag+" Create client failed to get OutputStream");
        e.printStackTrace();
      }
    } 
    public String getIpadr() {
      return s.getInetAddress().getHostAddress().trim();
    }
    public void close() throws IOException {
      s.close();
    }
    public boolean isClosed() {return s.isClosed();}
    /** set data to client
     *@param buf The data buffers
     *@param len The length of the write
     */
    public void send(byte [] buf, int len) throws IOException {
      try {
        if(!s.isClosed()) out.write(buf, 0, len);
      }
      catch (IOException e) {
        try {
          if(!s.isClosed()) s.close();
          throw e;
        }
        catch(IOException e2) {
          if(e2.getMessage() != null) {
            if(e2.getMessage().contains("Broken pipe")) Util.prta(tag+" Client go Broken pipe while closing bad socket");
            else if(e2.getMessage().contains("onnection reset")) Util.prta(tag+" client close with connection reset");
            else Util.prt(tag+" Client IOException while closing bad socket1="+e.getMessage());
          }
          throw e2;
        }
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
          if(!s.isClosed()) s.close();
          throw e;
        }
        catch(IOException e2) {
           if(e2.getMessage() != null) {
            if(e2.getMessage().contains("Broken pipe")) Util.prta(tag+" Client go Broken pipe while closing bad socket");
            else Util.prt(tag+" Client IOException while closing bad socket1="+e.getMessage());
          }
          throw e2;
        }
      }
    }
    
  } // end of class Client


  /* testing main.  Normally this is an object in a larger scheme */
  public static void main(String[] args) {
    Util.prt(Util.asctime());
    CmdListen server = new CmdListen(7995);//for testing listen to RTS_COMMAND_PORT
    
  }
}