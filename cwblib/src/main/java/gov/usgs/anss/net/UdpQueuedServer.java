/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * UDPQSrv.java
 *
 * Created on July 8, 2007, 3:35 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.net;
import java.net.DatagramSocket;

import java.net.UnknownHostException;
import java.net.SocketAddress;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import gov.usgs.anss.util.*;

/** This class is a generalized Client which listens to a UdpPort and puts the messages
 * in a queue.  The user sets this up and it keeps putting stuff in the Queue and the user
 * empties the queue with calls to dequeue().  If the queue overflows the data are discarded.
 * Status includes number of discarded packets, packets processed, queue highwater.
 * to be discarded.
  *
 * @author davidketchum
 */

public class UdpQueuedServer extends Thread {
  
  int MAX_LENGTH;         // Maximum length of received data
  String localIP;         // Local IP address (useful if multi-homed!)
  int port;               // port to listen to
  byte [] buf;
  int rcvBufSize;         // if user wants a bigger rcv buf size

  // These are eneeded if TCP/IP is used
  DatagramSocket ss;          // The datagram socket
  int bufsize;
  ArrayList<byte []> bufs;    // The array list of bufsize buffers
  int [] lengths;           // The actual length put in those buffers
  SocketAddress [] froms;   // The socket addresses the packet came from
  int nextin;               // Next place to put a rcvd packet
  int nextout;              // next place a user would dequeue a packet
  int ndiscard;             // # packets discarded on queue full
  long totalrecs;           // total recs processed
  int maxused;              // High water mark of used packets in the queue.
  String tag;               // Basically the IP and port 
  
  // status and debug
  long lastStatus;
  boolean terminate;
  int lastLength;
  SocketAddress lastSock;
  boolean dbg;
  public void sendPacket(DatagramPacket p, int len) throws IOException  {
    ss.send(p);
  }
  /** return the SocketAddress the last dequeued packet came from
   *@return the SocketAddress of the last dequeued packet */
  public SocketAddress getSockAddress() {return lastSock;}
  /** return number of packets received 
   * @return Number of packets returned.*/
  public long getNpackets() {return totalrecs;}
  /** Creates a new instance of TraceBufQueuedClient
   * @param locip ip address
   * @param pt port 
   * @param maxQueue Number of messages to reserve space for in queue
   * @param maxLength Maximum record length for these messages
   * @param rcvbufsize set rcvbuf size to this value in bytes.
   * @throws java.net.UnknownHostException
   */
  public UdpQueuedServer(String locip, int pt, int maxQueue, int maxLength, int rcvbufsize) throws UnknownHostException {
    bufsize=maxQueue;
    port = pt;
    rcvBufSize=rcvbufsize;
    localIP = locip;
    MAX_LENGTH = maxLength;
    buf = new byte[MAX_LENGTH];
    bufs = new ArrayList<>(bufsize);
    froms = new SocketAddress[bufsize];
    nextin=0;
    terminate=false;
    nextout=0;
    lengths = new int[bufsize];
    tag = "["+locip+"/"+pt+"]";
    for(int i=0; i<bufsize; i++) {
      bufs.add(i, new byte[MAX_LENGTH]);
    }
    start();
   
  }
 
  public String getStatusString() {return tag+" nblks="+totalrecs+" discards="+ndiscard+
      " in="+nextin+" out="+nextout+" qsize="+bufsize+" maxused="+maxused;}
  @Override
  public String toString() {return getStatusString();}
  public void terminate() {terminate=true;}
  public void setDebug(boolean t) {dbg=t;}
  public boolean isQueueFull() {
    int next = nextin+1;
    if(next >= bufsize) next=0;
     return next == nextout;   
  }
  public synchronized int getNextout() {return nextout;}
  public synchronized int dequeue(byte [] buf) { 
    if(nextin == nextout) return -1;
    int ret=0;
    try {
      System.arraycopy(bufs.get(nextout), 0, buf, 0,  lengths[nextout]);
      ret = lengths[nextout];
      lastSock = froms[nextout];
    } 
    catch(ArrayIndexOutOfBoundsException e) {// This is to look for a bug!
      Util.prta(tag+"OOB nextout="+nextout+
          " len="+buf.length+" "+bufs.get(nextin).length);
      Util.prta(tag+" arraycopy OOB exception e="+e.getMessage());
      e.printStackTrace();
      Util.exit(0);
    }
    nextout++;
    if(nextout >= bufsize) nextout=0;
    return ret;
  }
  /**write packets from queue*/
  @Override
  public void run() {
    boolean connected;
    StringBuilder sb = new StringBuilder(1000);
    
    int err=0;
    DatagramPacket d = new DatagramPacket(new byte[MAX_LENGTH], MAX_LENGTH);
    byte [] okays = new byte[3];
    
    // In UDP case data is sent by the send() methods, we do nothing until exit
    try {
      if(localIP.equals("")) {
        Util.prt(tag+" Listen for UDP packets on port="+port+" max len="+MAX_LENGTH);
        ss = new DatagramSocket(port);
      }
      else {
        InetAddress inet = InetAddress.getByName(localIP);
        Util.prt(tag+"Listen for UDP packets on locip="+localIP+" inet="+inet.getHostAddress()+"/"+port+" max length="+MAX_LENGTH);
        ss = new DatagramSocket(port, inet);      // listen for packets on this port
      }
      if(rcvBufSize > 0) {
        Util.prt(tag+"Rcv buf size def="+ss.getReceiveBufferSize());
        ss.setReceiveBufferSize(rcvBufSize);
        Util.prt(tag+"Rcv buf size def="+ss.getReceiveBufferSize());
      }
    }
    catch(IOException e) {
      Util.prt("could not set up datagram socket e="+e.getMessage());
    }
    
    int nused;
    // This loop establishes a socket
    while( !terminate) {
      try { 
        ss.receive(d);
        if(dbg) { 
          byte [] data = d.getData();
          Util.prta("Got UDP lent="+d.getLength()+" [0]="+data[0]+" [1]="+data[1]+" [2]="+data[2]+" [3]"+data[3]+
              " nxt="+nextin+" "+d.getAddress()+"/"+d.getPort()+d.getSocketAddress().toString());
        }
        if(dbg) 
          if((nextin+1) % bufsize == nextout) {
            ndiscard++;
            Util.prt("  **** Have to discard a tracebuf nextout="+nextout+" nextin="+nextin);
            continue;
          }
        lengths[nextin] = d.getLength();
        froms[nextin] = d.getSocketAddress();

        System.arraycopy(d.getData(), 0, bufs.get(nextin), 0, d.getLength());
        nextin = (++nextin) % bufsize;
        
        nused = nextin - nextout;
        if(nused < 0) nused += bufsize;
        if(nused > maxused) maxused=nused;
        totalrecs++;
        if(totalrecs % 1000 == 0) 
          if(System.currentTimeMillis() - lastStatus > 300000) {
            Util.prta(this.getStatusString());
            lastStatus = System.currentTimeMillis();
            totalrecs=0;
          }
          
      }
      catch(IOException e) {
        Util.IOErrorPrint(e,"UDPQSrv: *** IOError getting UDP "+port+" "+e.getMessage());
      }
    }
   // Outside loop that opens a socket 
    Util.prta("UDPQSrv: ** TraceBufQueuedClient terminated ");
    ss.close();
    Util.prta("UDPQSrv:  ** exiting");
  }

  public static void  main(String [] args) {
    Util.setModeGMT();

    
  }

}

