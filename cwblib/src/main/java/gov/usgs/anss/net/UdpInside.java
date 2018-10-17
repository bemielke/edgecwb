/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * UdpInside.java
 *
 * Created on August 22, 2003, 9:56 AM
 */

/**
 *
 * @author  ketchum
 * This class listens to the UdpServer which is outside of the fire wall.  It opens a socket
 * like any other client, uses the RTSMonitor class to parse and take action on each packet,
 * and prints status.  This program should be run INSIDE the firewall using its connection to
 * go to a UdpServer in the DMZ.  This program is intended to eventually update the RTS status
 * database in MySQL with latest data.  
 */
package gov.usgs.anss.net;
import java.net.*;
import java.io.*;
import java.util.*;
import gov.usgs.anss.util.*;

public class UdpInside extends Thread {
  private Socket s;
  private InputStream in;
  private final ArrayList<RTSMonitor> stations = new ArrayList<>(200);
  private final String host;
  private final int port;
  public UdpInside(String hostin, int portin) {
    byte [] buf = new byte[200];
    host = hostin; 
    port = portin;
    start();
  }
  
  @Override
  public void run() {   byte [] buf = new byte[400];
    byte [] rawaddr = new byte[4];
    InetAddress IP;
    GregorianCalendar now, lastStatus;
    lastStatus = new GregorianCalendar();   // Initialize it for 1st usage
    int nbytes;
    Util.prt("UdpInside : Running...");
    while(true) {
      while(true) {
        try {
          Util.prt(Util.asctime()+" UdpInside: Try to Open "+host+"/"+port); 
          s = new Socket(host, port);
          in = s.getInputStream();
          Util.prt(Util.asctime()+" UdpInside : Open Succeeded!");
          break;
        }
        catch (UnknownHostException e) 
        {   Util.UnknownHostErrorPrint(e, "UdpInside: Unknown host Opening "+host+"/"+port);
        }
        catch (SocketException e) 
        {   //Util.SocketErrorPrint(e, "UdpInside Opening "+host+"/"+port);
          Util.prt(Util.asctime()+" UdpInside  opening "+host+"/"+port+" "+e.getMessage());
        }
        catch (IOException e) {
          Util.IOErrorPrint(e,"UdpInside : opening "+host+"/"+port);
        }
        try {
          sleep(20000);
        } catch(InterruptedException e) {
          Util.prt(Util.asctime()+" UdpInside - Unusual : sleep interrupted!");
        }
      }
      Util.prt("Start infinite read loop");
      while (true) {
        try {
          nbytes = in.read(buf);
          //Util.prt("Read : len="+nbytes+" "+buf[0]+" "+buf[1]+" "+buf[2]+" "+buf[3]);
          if(buf[0] == 27 && buf[1] == 3  && nbytes > 0) {
            for(int i=0; i<4; i++) rawaddr[i]=buf[i+18];
            IP = InetAddress.getByAddress(rawaddr);
            //Util.prt(Util.asctime()+" "+IP+" Read : len="+nbytes+" "+buf[0]+" "+buf[1]+" "+buf[2]+" "+buf[3]);
            boolean found=false;
            for (RTSMonitor station : stations) {
              //Util.prt("look "+p.getAddress().toString()+" = "+ ((Sender) senders.get(i)).getInetAddress().toString());
              if (IP.equals(((RTSMonitor) station).getAddress())) {
                RTSMonitor rts = (RTSMonitor) station;
                rts.update(buf);
                found=true;
                break;
              }
            }
                     // Send to the client computers
            if( !found) {
              stations.add( new RTSMonitor(IP));
              Util.prt(Util.asctime()+" UdpInside : Add station "+IP+" # stations="+stations.size()); 
              ((RTSMonitor) stations.get(stations.size()-1)).update(buf);
            }
            
            // Print out periodic status
            now = new GregorianCalendar();
            // Is it time for status yet
            if( (now.getTimeInMillis() - lastStatus.getTimeInMillis()) > 300000) {
              StringBuilder st = new StringBuilder(1000);
              st.append(Util.asctime()).append(" ");
              for(int i=0; i<stations.size(); i++) {
                RTSMonitor rts = (RTSMonitor) stations.get(i);
                InetAddress inet = rts.getAddress();
                st.append(" ").append(inet.toString()).append(" ").append(rts.getNumberOfPackets());
                rts.resetNumberOfPackets();
                if( (i % 8) == 7) st.append("\n");
              }
              Util.prt(st.toString());
              lastStatus = now;
            }
          }
          else {
            if(nbytes == -1) {    // This is an EOF!
              Util.prt(Util.asctime()+" EOF on Socket read : Close down socket. len="+nbytes);
              try {
                in.close();
                s.close();
              } catch(IOException e2) {
                Util.IOErrorPrint(e2,"closing input stream");
              }
              break;        // break forces reopen of unit
            }
            Util.prt(Util.asctime()+ "Lead In not right! : len="+nbytes+" "+buf[0]+" "+buf[1]+" "+buf[2]+" "+buf[3]);
         }
        } catch (UnknownHostException e) {
           Util.UnknownHostErrorPrint(e, "UdpInside : read tcp unknown contributor "+
            rawaddr[0]+"."+rawaddr[1]+"."+rawaddr[2]+"."+rawaddr[3]);
        }
        catch (IOException e) {
          Util.IOErrorPrint(e,"Reading from pipe.  CLose up");
          try {
            in.close();
            s.close();
          } catch(IOException e2) {
            Util.IOErrorPrint(e2,"closing input stream");
          }
          break;        // break forces reopen of unit
        }
      }       // while forever
    }         // out while forever - loop reopens socket
  }
/*  public void run() 
  {
    byte [] buf= new byte[1024];
    DatagramPacket p = new DatagramPacket(buf, 1024);
    while(true) 
    {
      try 
      { d.receive(p);
        //Util.prt(Util.asctime()+" UdpListen: Rcv adr="+p.getSocketAddress()+
            //" len="+p.getLength());
        boolean found=false;
        GregorianCalendar now = new GregorianCalendar();
        for(int i=0; i<senders.size(); i++) {
          //Util.prt("look "+p.getAddress().toString()+" = "+ ((Sender) senders.get(i)).getInetAddress().toString());
          if(p.getAddress().equals( ((Sender) senders.get(i)).getInetAddress())) {
            Sender s = (Sender) senders.get(i);
            s.setLastTime( now);
            s.updateRTS(p.getData());
            found=true;
            break;
          }
        }
        
        // Send to the client computers
        if( !found) {
          Util.prt("UdpListen : Add station "+p.getAddress()); 
          senders.add( new Sender(p.getAddress()));
          ((Sender) senders.get(senders.size()-1)).updateRTS(p.getData());
        }
        server.sendStatus(p.getData(), p.getLength());
        
        
        

        // Is it time for status yet
        if( (now.getTimeInMillis() - lastStatus.getTimeInMillis()) > 300000) {
          StringBuffer st = new StringBuffer(300);
          st.append(Util.asctime()+" Udp From ");
          for(int i=0; i<senders.size(); i++) {
            Sender sd = (Sender) senders.get(i);
            InetAddress inet = sd.getInetAddress();
            st.append(" "+inet.toString()+" "+sd.getNumberOfPackets());
            sd.resetNumberOfPackets();
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
  }*/
  /** Creates a new instance of UdpInside */

 /**
  * @param args the command line arguments
  */
  public static void main(String[] args) {
     Util.prt(Util.asctime());
    UdpInside s= new UdpInside("localhost", 7997);
    Util.prt("Main UdpInside Returned!");

  }
  
}
