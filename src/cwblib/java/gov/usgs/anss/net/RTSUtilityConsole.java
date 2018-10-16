/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.net;

/*
 * RTSUtilityPanel.java
 *
 * Created on December 30, 2003, 10:45 AM
 */

/**
 *
 * @author  ketchum
 */
import gov.usgs.anss.util.*;
import java.io.*;
import java.net.*;
/** implement the Utility screen functions : download RTS, reset, and query
 * This listens for connections from the GUI and executes command via the redboot
 * port 4606 and puts results out the connecting socket
 *
 * @author davidketchum
 */
public class RTSUtilityConsole
{
  DownloadBurnsocket dl;
  RTSQuery qy;
  RTSReset rs;
  String ipadr;
  int offset;
  PrintStream lstout;

  /** Creates new form RTSUtilityPanel 
   * @param ip The ip address for this RTSUtility console
   * @param o The printsream on which to put any output
   */
  public RTSUtilityConsole(String ip, PrintStream o)
  { dl=null; qy=null; rs=null; ipadr=ip; lstout = o;
   
  }
  
 

  public void doDownload(boolean flag311)
  {
    // Add your handling code here:
    if(okToRun()) {dl = new DownloadBurnsocket(flag311); dl.waitDone();}
  }
  /*private void wait(int l) {
    long start = System.currentTimeMillis();
    while ( (System.currentTimeMillis() - start) < l) {}
  }*/
  public void doReset()
  {
    // Add your handling code here:
    if(okToRun()) {rs = new RTSReset(); rs.waitDone();}
  }
  public void doStatus()
  { if(okToRun()) {qy = new RTSQuery(false); Util.prta("wait for query");qy.waitDone();Util.prta("Query done");}
  }
  
  
  // End of variables declaration
  public boolean okToRun() {
    boolean ret=true;
    if(qy != null) 
      if(qy.isRunning()) ret = false;
    if(dl != null)
      if(dl.isRunning()) ret = false;
    if(rs != null)
      if(rs.isRunning()) ret = false;
    if( !ret ) {
      lstout.println(Util.asctime()+" "+"Cannot execute.  A socket thread is already running!");
    }
    return ret;
  }
  
  
  private class DownloadBurnsocket extends Thread {
    boolean running;
    boolean flag311;
    public DownloadBurnsocket(boolean f311) {
      running=true;
      flag311=f311;
      start();
    }
    public boolean isRunning() {return running;}
    public void waitDone() {while(running) {try {sleep(10L);} catch(InterruptedException e){}}}
    
    @Override
    public void run() {
      byte [] b = new byte[1408];
      b[0]=1;
      b[1]=0;
      offset = 0;
      
      /* We do a "query" to insure that the RTS bootloader is responding correctly.  
       *If not it is dangerous and senseless to send a download! 
       2) We do a reset to make sure the Boot loader is handling port 4606
       3) We do another Query and use the Memory information to insure the boot loader is running
       4) We do the download.
       5) At end we do a "GO" command.  Since a lot of data might be "in the air" we
          DO NOT close the socket until 10 seconds have past to ensure delivery before
         closing.  If not, the GO can get lost in the close!
       */
      lstout.println(Util.asctime()+" "+"Download : Doing a Query");
      qy = new RTSQuery(true);
      while( qy.isRunning()) {
        try{ sleep(1000L);} catch(InterruptedException e) {}
      }
      if(!qy.wasSuccessful()) {
        lstout.println(Util.asctime()+" "+" Abort download!");
        running=false;
        return;
      }

      if(!qy.getRunningCode().contains("DeviceMaster")) {

      
        lstout.println(Util.asctime()+" "+"Download : Do a reset");
        rs = new RTSReset();
        while( rs.isRunning()) {
          try{ sleep(1000L);} catch(InterruptedException e) {}
        }

        lstout.println(Util.asctime()+" "+"Wait 4 seconds for Reset to process.");
        try{ sleep(4000L);} catch(InterruptedException e) {lstout.println("Wait 3 interrupted");}

        qy = new RTSQuery(true);
        while( qy.isRunning()) {
          try{ sleep(50L);} catch(InterruptedException e) {}
        }
        if(!qy.wasSuccessful()) {
          lstout.println(Util.asctime()+" "+" Abort download!");
          running=false;
          return;
        }
      }
      if(qy.getAvailMemory() != 0 || qy.getAvailMemoryLength() < 7000000) {
        lstout.println(Util.asctime()+" "+" Abort Download 2. "+qy.getAvailMemory()+" "+qy.getAvailMemoryLength());
        running=false;
        return;
      }
      
      String filename=Util.fs+"home"+Util.fs+"vdl"+Util.fs+"burn-socket.bin";
      if(Util.getNode().contains("gldketchum3")) filename=Util.fs+"Users"+Util.fs+"ketchum"+Util.fs+"burn-socket.bin";
      if(flag311) filename=filename.replace("socket.bin", "socket311.bin");
      Socket s=null;
      try {
        try(FileInputStream in = new FileInputStream(filename)) {InetSocketAddress  adr = new InetSocketAddress(ipadr, 4606);
        s = new Socket(adr.getAddress(), 4606);OutputStream out = s.getOutputStream();
        int seq=0;
        int len=1;
        lstout.println(Util.asctime()+" "+"Download starting "+filename);
        long start = System.currentTimeMillis();
        WatchWrites watcher = new WatchWrites(s);
        while (len > 0) {
          int sz=1000;
          len = in.read(b,8,sz);
          lstout.print(".");
          //lstout.print("\r"+offset+" bytes"); 
          //lstout.println(Util.asctime()+" "+"Download "+offset);
          if(len <= 0 ) continue;
          // Set the length in the short in b[2] and b[3]
          b[1]= (byte) (seq++ & 0xff);      // set sequence
          b[2]= (byte) (len+8 & 0xff); 
          b[3]= (byte) ((len+8 >> 8) & 0xff);
          // set the offset in file in b[4] to b[8]
          b[4] = (byte) (offset & 0xff);
          b[5] = (byte) ((offset >> 8) & 0xff);
          b[6] = (byte) ((offset >> 16) & 0xff);
          b[7] = (byte) ((offset >> 24) & 0xff);
          //lstout.println(Util.asctime()+" "+"Download : len="+len+" sq="+(seq -1)+" off="+offset+" "+b[4]+" "+b[5]+" "+b[6]+" "+b[7]);
          try{ sleep(200L);} catch(InterruptedException e) {}
          if(len > 1300) {
            out.write(b, 0, 1300);
            lstout.print("%");
            try{ sleep(100L);} catch(InterruptedException e) {}
            out.write(b, 1300, len+8-1300);
          } else out.write(b,0,len+8);
          lstout.print(":");
          if(seq % 40 == 0) lstout.println("");
          offset = offset + len;
        }
        b[0]=3;
        b[1]=(byte) 0xd5;
        b[2]=8;
        b[3]=0;
        b[4]= 0x40;
        b[5]= 1;
        b[4]=0;
        b[5]=0;
        b[6]= 0;
        b[7]= 0;
        lstout.println("");
        lstout.println(Util.asctime()+" "+"Download : Complete. Send GO command.  Wait 30 seconds....");
        boolean ok=true;
        out.write(b,0,8);
        int wait = (int) (System.currentTimeMillis() - start + 999)/1000/3;
        if(wait < 60) wait=60;
        if(wait < 30) wait = 31;
        try{sleep(30000);} catch(InterruptedException e) {}
        s.close();
        /*while(ok) {
        lstout.println("Wait "+wait+" seconds before close - wait for 'Command Completed'..");
        try{
        sleep(15*1000);
        ok=false;
        } catch(InterruptedException e)
        {lstout.println("interrupted");}
        }*/
        //s.close();
        }        lstout.println(Util.asctime()+" "+"Download : Completed");
      } 

      catch(FileNotFoundException e) {
        lstout.println(Util.asctime()+" "+"Download : File Not found for file="+filename);
      }
      catch (SocketException e) {
        if(e.toString().indexOf("ocket close") >=0) lstout.println("\n"+Util.asctime()+" Socket has been closed.");
        else lstout.println(Util.asctime()+" "+"Download : SocketException setting SOLINGER?\n"+
            "message="+e.getMessage());
      }
      catch(IllegalArgumentException e) {
        lstout.println(Util.asctime()+" "+"Download : IllegalArgument building inetSocketAdr="+ipadr+e.getMessage());
      }
      catch (UnknownHostException e) {
        lstout.println(Util.asctime()+" "+"Download : Unknown Host downloading error host="+ipadr+e.getMessage());
      }
      catch (IOException e) {
        Util.IOErrorPrint(e,"Download : Downloading IO error");
        lstout.println(Util.asctime()+" "+"Download : socket I/O error-"+e.getMessage());
      }
      if(s != null) if(!s.isClosed()) try{s.close();} catch(IOException e) {}
      running=false;
    }
    private class WatchWrites extends Thread {
      Socket s;
      public WatchWrites(Socket ss) {
        s = ss;
        start();
      }
      @Override
      public void run() {
        int lastoffset=0;
        try {
          int bad=0;
 
          for(;;) {
            sleep(10000);
            if(offset == lastoffset) bad++;
            if(bad > 3) {
              lstout.print("\n"+Util.asctime()+" Timed out during download. bad="+bad+
                      " offset="+offset+" lastoff="+lastoffset+" Abort....\n");
              if(!s.isClosed()) {Util.prta("Download timed out.  Force close. ");s.close();}
              return;
            }
            lastoffset=offset;
            if(lastoffset > 300000) {lstout.println("WatchWrite exit normal.");return;}
          }
        }
        catch(InterruptedException | IOException e) {}
      }
    }
  }
  private class RTSQuery extends Thread {
    boolean successful;
    boolean running;
    int availMem;
    int availMemLen;
    boolean skipUSGSInfo;
    StringBuffer runningCode = new StringBuffer(30);
    public RTSQuery(boolean noUSGSInfo) {
      availMem=-1;
      availMemLen=-1;
      running=true;
      successful=true;
      skipUSGSInfo=noUSGSInfo;
      start();
    }
    public String getRunningCode() {return runningCode.toString();}
    public void waitDone() {while(running) {try {sleep(10L);} catch(InterruptedException e){}}}
    public boolean isRunning() { return running;}
    public boolean wasSuccessful() { return successful;}
    public int getAvailMemory() {return availMem;}
    public int getAvailMemoryLength() {return availMemLen;}
    
    @Override
    public void run() {
      byte [] b = new byte[20];
      byte [] q = new byte[10200];
      Socket s=null;
      //b[0]= 13; b[1]=0; 
      for(int i=0; i<20; i++) b[i]=0;
      b[0] = 5; b[1]=0;   // Query request
      b[2]=8;
      try {
        lstout.println(Util.asctime()+" "+"RTSQuery : Build adr="+ipadr);
        InetSocketAddress  adr = new InetSocketAddress(ipadr, 4606);
        lstout.println(Util.asctime()+" RTSQuery : parsed IP="+adr);
        Util.prta("query to "+ipadr+" open socket");
        s = new Socket(adr.getAddress(), 4606);
        Util.prta("Query to "+ipadr+" socket is opened.  Write message");
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();
        out.write(b,0,8);
        lstout.println(Util.asctime()+" "+"RTSQuery : Wait for Query Response");
        int loop=0;
        while(in.available() < 107){
          try {
            sleep(100L);
          }
          catch(InterruptedException e) {}
          if(loop++ > 200) break;
        }
        Util.prta("Query to "+ipadr+" available >107="+in.available());
        if(in.available() >= 107) {
          lstout.println("Read in RTS response avail="+in.available());
          int len = in.read(q, 0, 111);
          StringBuilder sb = new StringBuilder(2000);
           lstout.println(Util.asctime()+" "+"RTSQuery : read2 returned len="+len+" 103="+q[103]);
         /* for(int i=0; i<len; i++) {
            sb.append(" "+q[i]+"="+((char) q[i]));
            if( i % 20 == 19) sb.append("\n");
          }*/
          sb.append("         RTS Query\nIP Addr      : ").append((int)q[0]& 0xff).append(".").append((int)q[1]& 0xff).append(".").append((int)q[2]& 0xff).append(".").append((int)q[3]& 0xff).append("\n");
          sb.append("Subnet Msk   : ").append((int)q[12]& 0xff).append(".").append((int)q[13]& 0xff).append(".").append((int)q[14]& 0xff).append(".").append((int)q[15]& 0xff).append("\n");
          sb.append("Gateway      : ").append((int)q[4]& 0xff).append(".").append((int)q[5]& 0xff).append(".").append((int)q[6]& 0xff).append(".").append((int)q[7]& 0xff).append("\nRunning      : ");

          for(int i=24; i<100; i++)
            if(q[i] >= 32) {sb.append(((char) q[i])); runningCode.append((char) q[i]);}
            else break;
          sb.append("\nMac Address  : ").append(Util.toHex(q[88]).substring(2)).
                  append(":").append(Util.toHex(q[89]).substring(2)).append(":").
                  append(Util.toHex(q[90]).substring(2)).append(":").
                  append(Util.toHex(q[91]).substring(2)).append(":").
                  append(Util.toHex(q[92]).substring(2)).append(":").
                  append(Util.toHex(q[93]).substring(2)).append("\n");
          sb.append("Architect    : ").append(q[98]).append("\nNum Ports    : ").append(q[99]).append("\n");
          availMem=(((int) q[19] & 0xff) << 24)+(((int) q[18] & 0xff) << 16)+
            (((int) q[17] & 0xff) << 8)+((int) q[16] & 0xff);
          StringBuilder append = sb.append("AvailMemAddr : ").append(availMem).append(" ").append(Util.toHex(availMem)).append("\n");
          availMemLen=(((int) q[23] & 0xff) << 24)+(((int) q[22] & 0xff) << 16)+
            (((int) q[21] & 0xff) << 8)+((int) q[20] & 0xff);
          sb.append("AvailMemLen  : ").append(availMemLen).append(" ").append(Util.toHex(availMemLen)).append("\n");
            int model=(((int) q[94] & 0xff) << 24)+(((int) q[95] & 0xff) << 16)+
            (((int) q[96] & 0xff) << 8)+((int) q[97] & 0xff);
          sb.append("Model        : ").append(model).append(" ").append(Util.toHex(model)).append("\n");
          /*sb.append("Tunnel       :");
          for(int i=0; i<15; i++) sb.append((char) q[99+i]);
          sb.append(" Alt ");
          for(int i=0; i<15; i++) sb.append((char) q[115+i]);
          int port = ((int) q[132] &0xff)*256+((int) q[132] &0xff);
          sb.append("/"+port);*/

          if(q[103] == 99 && !skipUSGSInfo) {
            lstout.println(sb.toString());
            Util.prta("QUery to "+ipadr+" wait for usgs info");
            lstout.println("USGS code 4.? found.  Wait for USGS info....");
            try {
              s.close();
            }
            catch(IOException e) {}
            s = new Socket(adr.getAddress(), 4606);
            out = s.getOutputStream();
            in = s.getInputStream();
            b[0]=99;
            out.write(b,0,8);
            while(in.available() < 2){
              try {
                sleep(100L);
              }
              catch(InterruptedException e) {}
              if(loop++ > 200) break;
            }
            in.read(q, 0, 2);
            int ndata = (((int) q[0] & 0xff) << 8)+((int) q[1] & 0xff);
            len = ndata;
            int pnt=0;
            loop=0;
            while (len > 0) {
              if(in.available() > 0) {
                int l = in.read( q, pnt, len); 
                if(l > 0) {len -= l; pnt += l;}
                if(l <= 0) break;
               lstout.println("l="+l+" pnt="+pnt);
              }
              else {
                loop++;
                if(loop > 100) break;
                Util.sleep(100);
              }
            }
            Util.prta("Left="+len+" of "+ ndata+" read="+pnt);
            lstout.println("\n\n ************* Console Buffer ******************* "+ndata+" "+(ndata-len)+"\n"+new String(q,0, ndata-len)+
                           "\n\n ************* Console Buffer ******************* "+ndata+" "+(ndata-len)+"\n");
          }
          lstout.println(sb.toString());
          if(s != null) s.close();
          lstout.println(Util.asctime()+" RTSQuery : Successful");
        }
        else {
          lstout.println(Util.asctime()+" RTSQuery : timed out waiting for response. Abort ... "+in.available()+" "+loop);
          successful=false;
        }
        if(!s.isClosed()) s.close();
      }
      catch(IllegalArgumentException e) {
        lstout.println(Util.asctime()+" "+"RTSQuery : IllegalArgument building inetSocketAdr="+ipadr+e.getMessage());
        successful=false;
      }
      catch (UnknownHostException e) {
        lstout.println(Util.asctime()+" "+"RTSQuery : Unknown Host error host="+ipadr+e.getMessage());
        successful=false;
      }
      catch (IOException e) {
        Util.SocketIOErrorPrint(e,"RTSQuery : error opening Socket");
        lstout.println(Util.asctime()+" "+"Query : I/O error -"+e.getMessage());
        successful=false;
      }
      Util.prta("Query to "+ipadr+" is done.");
      try {if(s != null) if(!s.isClosed()) s.close();} catch(IOException e) {}
      running=false;
    }
  }
  private class RTSReset extends Thread {
    boolean running;
    public RTSReset() {
      running=true;
      start();
    }
    @Override
    public void run() {
      byte [] b = new byte[8];
      b[0]= 13; b[1]=0; 
      b[2]=8; b[3]=0; b[4]=0; b[5]=0; b[6]=0; b[7] = 0;
      Socket s=null;
      try {
        lstout.println(Util.asctime()+" "+"RTSReset : Build adr="+ipadr);
        InetSocketAddress  adr = new InetSocketAddress(ipadr, 4606);
        s = new Socket(adr.getAddress(), 4606);
        OutputStream out = s.getOutputStream();
        out.write(b,0,8); 
        try {sleep(1000);} catch(InterruptedException e) {}
        lstout.println(Util.asctime()+" "+"RTSReset : Done.");
      } 
      catch(IllegalArgumentException e) {
        lstout.println(Util.asctime()+" "+"RTSReset : IllegalArgument building inetSocketAdr="+ipadr+e.getMessage());
      }
      catch (UnknownHostException e) {
        lstout.println(Util.asctime()+" "+"RTSReset : Unknown Host error host="+ipadr+e.getMessage());
      }
      catch (IOException e) {
        if(e.getMessage() != null) {
          if(e.getMessage().contains("Connection timed out")) lstout.println("RTSReset: Connection timed out");
          else Util.IOErrorPrint(e,"RTSReset : error opening Socket",lstout);
        }
        else Util.IOErrorPrint(e,"RTSReset : error opening Socket", lstout);
      }
      try {
        if(s != null ) if(!s.isClosed()) s.close();
      }
      catch(IOException e) {}
      running=false;
    }
    public boolean isRunning() {return running;}
    public void waitDone() {while(running) {try {sleep(10L);} catch(InterruptedException e){}}}
  }     // ENd of class RTSQuery


  // This main displays the form Pane by itself
  public static void main(String args[]) {
    PrintStream lstout = System.out;
    lstout.println("Enter IP address for station");
    String ipadr=Util.getLine();
    if(ipadr.equals("")) ipadr=Util.getLocalHostIP();
    RTSUtilityConsole r = new RTSUtilityConsole(ipadr, System.out);
    boolean bail=false;
    while(true) {
      lstout.println("0 = Get Status, 1 = Download, 2 = Reset, 9 = Exit");
      String ans=Util.getLine();
      //lstout.println("got="+ans);
      if(ans.length() == 1) {
        switch (ans.charAt(0)) {
          case '0':
            r.doStatus();
            break;
          case '1':
            r.doDownload(false);
            break;
          case '2':
            r.doReset();
            break;
          case '9':
            bail=true;
            break;
          default:
            lstout.println("Not a valid selection ="+ans);
        }
      } else lstout.println("Selection invalid="+ans);
      if(bail) break;
    }
    lstout.println("exiting");
    
  }  
}
