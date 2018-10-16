/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.net;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Iterator;
import gov.usgs.anss.util.*;
/*
* UdpListen.java
* *
 *The list of senders is implemented via the Collections.synchronizedList(new ArrayList) 
 * so that the iterators on the list can be synchronized.  This is discuss briefly in 
 *Eckel pg 566.  You must wrap all uses of iterators in a "synchronized (senderss)" to 
 *insure the iterators access is properly synchronized.  When you do not do this you can get
 * ConcurrentModificationException in the iterator manipulations.
 *

* Created on July 29, 2003, 4:15 PM
*/

/**
*
* @author  ketchum
*/

public class UdpListen extends Thread{
  private DatagramSocket d;
  private CmdListen server;
  private final  List<Sender> senders = Collections.synchronizedList(new ArrayList<Sender>(100));;
  private GregorianCalendar lastStatus;
  /** Creates a new instance of UdpStatus
   * @param port
   * @param s Server to handle requests
   */
  public UdpListen(int port, CmdListen s) {
    try {
      server = s;
      d = new DatagramSocket(port);
      Util.prt("UdpListen: Open Port="+port);
      
      lastStatus = new GregorianCalendar();
    }
    catch (SocketException e) 
    {   Util.SocketErrorPrint(e, "UdpListen: opening port="+port);
    }
    start();
  }
  @Override
  public void run() 
  {
    byte [] buf= new byte[1024];
    DatagramPacket p = new DatagramPacket(buf, 1024);
    while(true) 
    {
      try 
      { d.receive(p);
        /*Util.prt(Util.asctime()+" UdpListen: Rcv adr="+p.getSocketAddress()+
            " len="+p.getLength());*/
        boolean found=false;
        GregorianCalendar now = new GregorianCalendar();
        synchronized(senders) {
          Iterator<Sender> it = senders.iterator();
          while(it.hasNext()) {
            //Util.prt("look "+p.getAddress().toString()+" = "+ ((Sender) senders.get(i)).getInetAddress().toString());
            Sender s = (Sender) it.next();
            if(p.getAddress().equals( s.getInetAddress())) {
              s.setLastTime( now);
              found=true;
              break;
            }
          }

        
          // Send to the client computers
          if( !found) {
            Util.prt(Util.asctime()+" UdpListen : Add station "+p.getAddress()); 
            senders.add( new Sender(p.getAddress()));
          }
        }
        if(server != null) server.sendCmdAll(p.getData(), p.getLength());

        // Is it time for status yet
        if( (now.getTimeInMillis() - lastStatus.getTimeInMillis()) > 1800000) {
          StringBuilder st = new StringBuilder(1000);
          st.append(Util.asctime()).append(" Udp From ");
          synchronized (senders) {
            Iterator it = senders.iterator();
            int i = 0;
            while (it.hasNext()) {
              Sender sd = (Sender) it.next();
              InetAddress inet = sd.getInetAddress();
              st.append(" ").append(inet.toString()).append(" ").append(sd.getNumberOfPackets());
              sd.resetNumberOfPackets();
              if( (i % 8) == 7) st.append("\n"); i++;
            }
          }
          Util.prt(st.toString());
          lastStatus = now;
        }
      }
      catch (IOException e)
      { Util.prt("receive through IO execption");
        break;
      }
    }
  }
  private class Sender {
   InetAddress addr;
   GregorianCalendar lastTime;
   int numPackets;
   public Sender (InetAddress a) {
     addr = a;
     lastTime = new GregorianCalendar();
     numPackets=0;
   }
   public InetAddress getInetAddress() { return addr;}
   public GregorianCalendar getLastTime() { return lastTime;}
   public int getNumberOfPackets() {return numPackets;}
   public void setLastTime(GregorianCalendar d) { numPackets++; lastTime = d; }
   public void incNumberOfPackets() {numPackets++; }
   public void resetNumberOfPackets() { numPackets = 0; }
   
  }
  /**
  * @param args the command line arguments
  */
  public static void main(String[] args) {
    Util.prt(Util.asctime());
    Util.setModeGMT();
    
    // server data via a CmdListen
    CmdListen server = new CmdListen(AnssPorts.RTS_STATUS_SERVER_PORT);
    
    // Listen for UDP status packets from RTS
    UdpListen t = new UdpListen(AnssPorts.RTS_STATUS_UDP_PORT, server);

  }
}
