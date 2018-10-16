/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.util;

import gov.usgs.alarm.SendEvent;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
/**
 * simpleSMTP.java - This class is used to send a simple mail message 
 (no attachments, etc. just subject and text body) to a SMTP server.  The
 *main thread does the sending and an inner class reads data back from the
 *smtp server.  A StringBuilder is maintained with details of the session so
 *a user could inspect or use it
 * <p>
 * The property SMTPServer is used to send the mail.  If this is set to "mailx" then
 * mailx is used to send the mail via a batch file created in the temporary area.
 * <p>
 * Created on August 20, 2003, 4:56 PM
 *
 * @author  ketchum
 */

public final class SimpleSMTPThread extends Thread {
  public static String [] dow ={"NaN", "Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
  public static String [] months ={"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
  public static DecimalFormat df2 = new DecimalFormat("00");
  public static boolean debugOverride=false;
  private static String bindIP[] = new String[2];
  private static InetAddress bindAddr[] = new InetAddress[2];
  private static int [] bindport = new int[2];
  private StringBuilder err;
  private BufferedReader bin;
  private String server;
  private String to;
  private String from;
  private String subject;
  private String body;
  private boolean successful;
  private boolean running;
  private boolean dbg;
  private Socket s;
  private long connectTime;
  private final boolean alternate;
  /** 
   * 
   * @param ip1
   * @param ip2
   * @param pt1
   * @param pt2 
   */
  public static void setBinding(String ip1, String ip2, int pt1, int pt2) {
    bindIP[0]=ip1;
    bindIP[1]=ip2;
    bindport[0]=pt1;
    bindport[1]=pt2;
    try {
      bindAddr[0] = InetAddress.getByName(ip1);
      bindAddr[1] = InetAddress.getByName(ip2);
    }
    catch(UnknownHostException e) {
      Util.prt("Failed to bind the two addresses");
      bindAddr[0]=null;
      bindAddr[1]=null;
    }
  }
  public static void setDebug(boolean t) {debugOverride=t;}
  /** Create a object and send the mail message to he server.  This creates a
   *thread which sends the message and spawns an inner class which monitors and
   *records the responses of the SMTP server.  If subject line contains 'DBG' then
   * debugging output of the thread will be turned on.
   *@param serverin The SMTP server machine to use (port 25)
   *@param to_in The email address of the recipient
   *@param from_in The email address of the sender
   *@param subject_in The subject line
   *@param body_in The main body of the message
   */
  public SimpleSMTPThread(String serverin, String to_in, String from_in, String subject_in, String body_in) {
    alternate=false;
    doit(serverin, to_in, from_in, subject_in, body_in);
  }
  /** Create a object and send the mail message to he server.  This creates a
   *thread which sends the message and spawns an inner class which monitors and
   *records the responses of the SMTP server.  If subject line contains 'DBG' then
   * debugging output of the thread will be turned on.
   *@param serverin The SMTP server machine to use (port 25)
   *@param to_in The email address of the recipient
   *@param from_in The email address of the sender
   *@param subject_in The subject line
   *@param body_in The main body of the message
   * @param alt If true use alternate
   */
  public SimpleSMTPThread(String serverin, String to_in, String from_in, String subject_in, String body_in, boolean alt)
  { alternate=alt;
    doit(serverin, to_in, from_in, subject_in, body_in);
  }
  private void doit(String serverin, String to_in, String from_in, String subject_in, String body_in) {
    dbg=debugOverride;
    if(from_in == null) from = "vdl-unknown";
    else from = from_in.trim();
    if(from.equals("")) from = "vdl-unknown";
    try {
      if(!from.contains("@")) from += "@"+InetAddress.getLocalHost().getCanonicalHostName();
    }
    catch(UnknownHostException e) {}    // cannot do anything about this
    
    // If we are to use mailx, go ahead!
    boolean mailx=false;
    
    if(Util.getProperty("SMTPServer") == null) mailx=true;
    else if( Util.getProperty("SMTPServer").equals("") ||
            Util.getProperty("SMTPServer").equalsIgnoreCase("mailx")) mailx=true;
    Util.prt("SMTP doit: mailx sin="+serverin+" mailx="+mailx+" to="+to_in+" from="+from_in+"->"+from+" subj="+subject_in);
    if(mailx) {
      File temp = null;
      try {
        StringBuilder sb = new StringBuilder(100+body_in.length());
        String str = "#!"+Util.fs+"bin"+Util.fs+"bash\nmailx -s \""+subject_in+"\"";
        if(!Util.getOS().contains("Mac"))str +=" -S from="+from;  // MacOS does not support the "-S from=??"
        str +=" "+to_in+" <<-EOF\n"+body_in+"\nEOF\n";
        //Util.prt("Script:\n"+str);
        temp = File.createTempFile("mailx_lkjdskdjf",".tmp");
        try (RandomAccessFile rw = new RandomAccessFile(temp.getAbsoluteFile(),"rw")) {
          rw.seek(0);
          rw.write(str.getBytes());
          rw.setLength(str.length());
        }
        Subprocess sp = new Subprocess("bash "+temp.getAbsolutePath());
        sp.waitFor();
        if(sp.getOutput().equals("") && sp.getErrorOutput().equals("")) successful=true;
        temp.delete();
        err= new StringBuilder(10);
        err.append(sp.getOutput()).append(sp.getErrorOutput());
      }
      catch(InterruptedException e) {}
      catch(IOException e) {
         e.printStackTrace();
         if(temp != null) temp.delete();
         err = new StringBuilder(10);
         err.append(e.toString());
      }
      return;
    }
    
    // Below here the mail is sent by directly connecting to a SMTP server which has proven problematic!
    if(serverin == null || serverin.equals("")) server = Util.getProperty("SMTPServer");
    else server = serverin;
    if(server == null) {
      Util.prt("SMTPT: **** there is no SMTP server configured.  Abort!");
      SendEvent.debugEvent("SMTPThrErr","No SMTPServer configured "+server, this);
      return;
    }
    if(server.trim().isEmpty()) {
      Util.prt("SMTPT: **** there is not SMTP server configured.  Abort!");
      return;
    }    
    Util.prt("SMTPT: serverin="+serverin+" server="+server+" SMTPServer.prop="+Util.getProperty("SMTPServer")+" fromin="+from_in+" toin="+to_in);

    if(subject_in.contains("DBG")) dbg=true;
    to = to_in.trim();
    subject=subject_in.trim();
    body=body_in.trim();
    running=true;
    if(dbg) Util.prt("SMTPT: serv="+server+"HostIP="+Util.getProperty("HostIP")+
            " to "+to+" from "+from+"\nsub="+subject);
    successful=false;
    err= new StringBuilder(1000);
    setDaemon(true);
    start();
  }
  /** this is the main body of the sending thread.  It interacts with the SMTP
   *server in the normal manner and monitors responses of the server via a GetSession
   *inner class object.  Beware per http://pobox.com/~djb/docs/smtplf.html sending linefeeds
   *without preceding carriage returns can result in rejections of mail by some servers
   *per a RFP spec.  We spent much time running this down!
   */
  @Override
  public void run() {
    try
    { if(err.length() > 0) err.delete(0, err.length()-1);
      connectTime=System.currentTimeMillis();
      try {
        if(bindAddr[0] == null) {
          //if(dbg)
            Util.prta("SMTPT:Open socket to "+server);
          s = new Socket(server, 25);
        }
        else {
          for(int i=0; i<10; i++) {
            try {
              int j;
              if(alternate) {
                j=1;
                s = new Socket(server, 25, bindAddr[j], bindport[j]+i*2);
                //if(dbg)
                  Util.prta("SMTPT:Open socket to "+server+" bind="+bindIP[j]+"/"+(bindport[j]+i*2)+" alternate binding");
              }
              else {
                j=0;
                //if(dbg)
                  Util.prta("SMTPT:Open socket to "+server+" bind="+bindIP[j]+"/"+(bindport[j]+i*2)+" normal binding");
                s = new Socket(server, 25, bindAddr[j], bindport[j]+i*2);
              }
              break;
            }
            catch(BindException e) {Util.prta("Bind exception i="+i);}
            catch(java.io.IOException e) {
              Util.prt("Trying alternate binding go error for i="+i+" alt="+alternate);
              e.printStackTrace();
              if(i < 9) continue;
              if(e.getMessage() != null) {
                if(e.getMessage().contains("Connection timed out")) {
                  Util.prta("SMTPT: 1st Connection timed out to server="+server+" to "+to+
                          " subj="+subject+" "+(System.currentTimeMillis() - connectTime));
                  connectTime=System.currentTimeMillis();
                  s = new Socket(server, 25);
                }
                else {
                  Util.prta("SMTPT: IOError"+e+" server="+server+" to "+to+" subj="+subject+" "+
                          (System.currentTimeMillis() - connectTime));
                  SendEvent.debugEvent("SMTPThrErr","IOError e="+e,this);
                  running=false;
                  return;
                }
              }
              else {
                Util.prt("SMTPT: null IOexception="+e.toString()+" server="+server+" to "+to+" subj="+subject);
                err.append("SimpleSMTPThread exception : ").append(e.toString()).append(" server=").append(server);
                SendEvent.debugEvent("SMTPThrErr","IOException e="+e, this);
                running=false;
                return;
             }
            }
          }

        }
      }
      catch (java.io.IOException e)
      { if(e.getMessage() != null) {
          if(e.getMessage().contains("Connection timed out")) {
            Util.prta("SMTPT: 1st Connection timed out to server="+server+" to "+to+
                    " subj="+subject+" "+(System.currentTimeMillis() - connectTime));
            connectTime=System.currentTimeMillis();
            s = new Socket(server, 25);
          }
          else {
            Util.prta("SMTPT: IOError"+e+" server="+server+" to "+to+" subj="+subject+" "+
                    (System.currentTimeMillis() - connectTime));
            SendEvent.debugEvent("SMTPThrErr","IOError e="+e,this);
            //e.printStackTrace();
            running=false;
            return;
          }
        }
        else {
          Util.prt("SMTPT: null IOexception="+e.toString()+" server="+server+" to "+to+" subj="+subject);
          err.append("SimpleSMTPThread exception : ").append(e.toString()).append(" server=").append(server);
          SendEvent.debugEvent("SMTPThrErr","IOException e="+e, this);
          running=false;
          return;
       }
      }
      //err.append("SimpleMail :Srv="+server+" to:"+to+" frm:"+from+" subj:"+subject+"\n");
      //Util.prt(err.toString());
      // connect with the mail server
     // get the input stream from the socket and wrap it in a BufferedReader
      //Util.prta("s="+s+"|"+s.getLocalAddress()+"/"+s.getLocalPort());
      InputStream in = s.getInputStream();
      bin = new BufferedReader(new InputStreamReader(in));
      GetSession get = new GetSession(bin, err);   // Thread listening for input!
      

      // get the output stream and wrap it in a PrintWriter
      //PrintWriter pout = new PrintWriter(s.getOutputStream(), true);
      OutputStream pout = s.getOutputStream();


      // say Hello back
      successful=true;
      String str;
      int i = server.indexOf('.');
      str = "HELO "+server.substring(i);
      if(Util.getProperty("HostIP") != null)
        if(Util.getProperty("HostIP").length() > 7) str="HELO "+Util.getProperty("HostIP");
      if(alternate && bindAddr[0] != null) str = "HELO "+bindIP[1].trim(); 

      if(dbg) System.out.println("SMTPT:"+str);	// display what we're sending
      err.append("Send:").append(str).append("\n");
      //pout.println(str+"\r");		// send it   
      pout.write((str+"\r\n").getBytes());

      // the message header
      if(from.trim().contains("<")) {
        String commonName="";
        String bracketName=from.trim();
        
        if(from.trim().indexOf("<") > 0) {
          commonName = from.trim().substring(0,from.trim().indexOf("<")).replaceAll("\"","");
          bracketName=from.trim().substring(from.trim().indexOf("<"));
        }
        if(commonName.equals("")) str = "MAIL FROM : "+bracketName.trim();
        else str = "MAIL FROM: \""+commonName.trim()+"\""+bracketName.trim();
      }
      else       str = "MAIL FROM: <"+from.trim()+">";  // from

      if(dbg)System.out.println("SMTPT:"+str+"|from="+from.trim()+"|");
      err.append("Send:").append(str).append("\n");
      //pout.print(str+"\r\f");
      pout.write((str+"\r\n").getBytes());
      if(!get.chkFor("sender","2.1.0","ok")) {
        err.append("NO 'Sender ok' abort...\n"); successful=false;}
      else {
        str = "RCPT TO: <"+to+">";  // to
        if(dbg)System.out.println("SMTPT:"+str+"|");
        err.append("Send:").append(str).append("\n");
        //pout.println(str+"\r");
        pout.write((str+"\r\n").getBytes());
        if(!get.chkFor("recipient","2.1.5","ok")) {
          err.append("NO 'Recipient ok' abort...\n"); successful=false;
          if(dbg) System.out.println("SMTPT:"+"No recipient o.k.");
        } 
        else {

          // send the DATA message
          str = "DATA";
          if(dbg) System.out.println("SMTPT:"+str);
          err.append("Send:").append(str).append("\n");
          //pout.println(str+"\r");
          pout.write((str+"\r\n").getBytes());
          if(!get.chkFor("354")) {
            err.append("NO 'go ahead or start input' abort...\n");  successful=false; 
            if(dbg) System.out.println("SMTPT:"+str);
          }
          else {

            // subject line
            pout.write(("SUBJECT:"+(alternate?"":"")+subject+"\r\n").getBytes()); // Add an A to subject if alternate
            err.append("Send:SUBJECT:").append(Util.asctime()).append(" ").append(subject).append("\n");
            
            pout.write(("From:"+from.trim()+"\r\n").getBytes());
            GregorianCalendar now = new GregorianCalendar();
            String d = "Date:"+dow[now.get(Calendar.DAY_OF_WEEK)]+", "+df2.format(now.get(Calendar.DAY_OF_MONTH))+" "+
                months[now.get(Calendar.MONTH)]+" "+now.get(Calendar.YEAR)+" "+
                df2.format(now.get(Calendar.HOUR_OF_DAY))+":"+df2.format(now.get(Calendar.MINUTE))+":"+
                df2.format(now.get(Calendar.SECOND))+" -0"+(-now.get(GregorianCalendar.ZONE_OFFSET)/36000)+"\r\n";
            pout.write(d.getBytes());
            pout.write("\r\n".getBytes());
            
            // message
            pout.write(convert(body).getBytes());
            pout.write("\r\n.\r\n".getBytes());
            
            err.append("Send:Body:").append(body).append("\n.\n");
           
            // send the QUIT message
            str = "QUIT";
            err.append("Send:").append(str).append("\n");
            pout.write((str+"\r\n").getBytes());
            if(dbg) System.out.println("SMTPT:"+str);
            //try{sleep(2000L);} catch(InterruptedException e) {}
            if(!get.chkFor("250")) {
              err.append("no 'Message Accepted' abort...\n");  successful=false;
            }
          }
        }
      }
      if(dbg || !successful) Util.prta("SMTPT:----- Error list --------\n"+err.toString());
      if(dbg || !successful) Util.prta("SMTPT:------Session --------- \n"+get.getSession());
          
      // close the connection
      get.setBin(null);
      s.close();
    }
    catch (java.io.IOException e) 
    { if(e.getMessage() != null) {
        if(e.getMessage().contains("Connection timed out")) {
          Util.prta("SMTPT: 2nd Connection timed out to server="+server+" to "+to+
                  " subj="+subject+" "+(System.currentTimeMillis() - connectTime));
          SendEvent.debugEvent("SMTPThrErr", "Connection2 timed out to "+server, this);
        } 
        else {
          Util.prta("SMTPT: IOError="+e+" to server="+server+" to "+to+
                  " subj="+subject+" "+(System.currentTimeMillis() - connectTime));
          SendEvent.debugEvent("SMTPThrErr", "IOerror2="+e+" server="+server, this);
        }
      }
      else {
        Util.prt("SMTPT: exception2="+e.toString()+" server="+server);
        err.append("SimpleSMTPThread exception : ").append(e.toString()).append(" server=").append(server);
        SendEvent.debugEvent("SMTPThrErr","IOException2 e="+e, this);
      }
    }
    catch(RuntimeException e) {
      Util.prta("SMTPThread got Runtime error="+e);
      e.printStackTrace();
    }
  
    if(s != null)
      if(!s.isClosed()) try{ s.close();} catch(IOException e) {}
    if(dbg) Util.prt("SMTPT: exiting");
    running=false;
  }
  private String convert(String body) {
     StringBuilder sb = new StringBuilder(body.length()+100);
     for(int i=0; i<body.length(); i++) 
       if(body.charAt(i) == '\n') sb.append("\r\n");
       else sb.append(body.charAt(i));
     return sb.toString();
  }
  /** wait for the SMTP mail session to complete.  This will sleep the caller until
   *the SMTP session has completed.  At the time of the return the session text will
   *be complete
   */
  public void waitForDone() {while(running) {try{sleep(200);} catch (InterruptedException e) {}}}
  /** Return whether the mail message appeared to go out successfully to the server
   *@return If true, the message was sent
   */
  public boolean wasSuccessful(){return successful;}
  /** Return a String with a record of the SMTP session.  Normally only used for debugging
   *the send.
   *@return The session string
   */
  public String getSendMailError() {
    return err.toString();
  }
  
  /** This inner class monitors the input side of the SMTP socket and adds any
   *text received from the server to the session log.
   */
    
  public final class GetSession extends Thread {
    String lastLine;
    BufferedReader bin;
    StringBuilder err;
    public String getSession() {return err.toString();}
    /** Construct a listener on the SMTP socket
    *@param b is the input side of the socket
     *@param e The session log to append received lines to
     */
    public GetSession(BufferedReader b,StringBuilder e) {
      bin=b; err=e;
      start();
    }
    /** return the last line of input received from theSMTP server
     * @return 
     */
    public String getLastLine(){return lastLine;}
    /** look for a string in the last line received.  Used by the SMTP sender to
     *insure good SMTP responses to output come back (case insensitive)
     *@param str to look for.
     *@return true if string is found on last line
     */
    public boolean chkFor(String str) {
      //Util.prt("ChkFor="+str);
      for(int i=0; i<100; i++) {
        if(!err.toString().toLowerCase().contains(str.toLowerCase())) {
          try {sleep(100L);} catch (InterruptedException e) {}
        } else return true;
      }   
      //Util.prt("Chkfor not found="+lastLine);
      return false;
    }
    /** look for one of two strings and string on the last line (like "recipient","2.1.5", "ok") case insensitive.
     * Returns true if either recipient or 2.1.5 appears with 'ok"
     *@param str1 first string to look for
     * @param str1a Or if this alternative is available.
     *@param str2 2nd string to look for
     *@return true of the strings are both found */
    public boolean chkFor(String str1, String str1a, String str2) {
      if(chkFor(str1)) 
        if(chkFor(str2)) return true;
      if(chkFor(str1a))
        if(chkFor(str2)) return true;
      return false;
      
    }    /** look for two string on the last line (like "recipient", "ok") case insensitive.
     *@param str1 first string to look for
     *@param str2 2nd string to look for
     *@return true of the strings are both found */
    public boolean chkFor(String str1, String str2) {
      if(chkFor(str1)) 
        if(chkFor(str2)) return true;
      return false;
      
    }
    /** conveniently clear the receive buffer input which shuts down the thread
     * @param b The buffered reader to set */
    public void setBin(BufferedReader b) {bin=b;}
    
    @Override
    public void run() {
      String line=null;
      while( bin != null) {
        try {line = bin.readLine();} catch (IOException e) {
          //Util.IOErrorPrint(e,"SMTPThread: IOerror");
          bin=null;
        }
        if(line == null) {
          try{sleep(10L);} catch (InterruptedException e) {}
        }
        else {
          lastLine=line;
          err.append("resp:").append(line).append("\n");
          //Util.prt("GetSession l="+line);
        }
      }
      //Util.prt("SMTPThread:GetSession: bin is null exit");
    }
  }
 /**
  * 
  * @param to The email address to send to.  If no '@', then usgs.gov is added
  * @param subject The subject of the email
  * @param body The body of the email
  * @return 
  */
 public static SimpleSMTPThread email(String to, String subject, String body) {
    String t = to;
   if(t == null) {
     Util.prta(" **** SimpleSMTPThread email() to is null or blank.  Use anss.alarm as a backup");
     t = "anss.alarm@usgs.gov";
   }
   if(t.equals("")) {
     t = "anss.alarm@usgs.gov";
     Util.prta(" **** SimpleSMTPThread email() to is null or blank.  Use anss.alarm as a backup");
   }
   if(!t.contains("@")) t += "@usgs.gov";
   String from = Util.getProperty("SMTPFrom");
   if(from == null) from="ketchum";
   if(!from.contains("@")) from += "@"+Util.getSystemName()+".cr.usgs.gov";
   Util.prta("email() to="+to+" t="+t+" from="+from+" SMTPFrom="+Util.getProperty("SMTPFrom"));
   return new SimpleSMTPThread("",t, from ,subject, body);
 }
 /**
  * 
  * @param to The email address to send to.  If no '@', then usgs.gov is added
  * @param subject The subject of the email
  * @param body The body of the email
  * @return 
  */  
 public static SimpleSMTPThread emailAlt(String to, String subject, String body) {
   String t = to;
   if(t == null) {
     Util.prta(" **** SimpleSMTPThread email() to is null or blank.  Use anss.alarm as a backup");
     t = "anss.alarm@usgs.gov";
   }
   if(t.equals("")) {
     t = "anss.alarm@usgs.gov";
     Util.prta(" **** SimpleSMTPThread email() to is null or blank.  Use anss.alarm as a backup");
   }
   if(!to.contains("@")) t += "@usgs.gov";
   String from = Util.getProperty("SMTPFrom");
   if(from == null)  from =  Util.getProperty("SMTPFrom");
   if(from == null) from="ketchum";
   if(!from.contains("@")) 
      from += "@"+Util.getSystemName()+".cr.usgs.gov";
   return new SimpleSMTPThread("",t, from ,subject, body, true);
 }

 /** A test for the SimpleSMTPThread class
     @param args the command line arguments
  */
  public static void main(String[] args) {
    Util.loadProperties("edge.prop");
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    Util.init();
    String subject=null;
    String filename=null;
    boolean alternate=false;
    int istart=0;
    for(int i=0; i<args.length; i++) {
      switch (args[i]) {
        case "-s":
          subject = args[i+1];
          istart=i+2;
          break;
        case "-smtp":
          Util.setProperty("SMTPServer",args[i+1]);
          i++;
          istart=i+1;
          break;
        case "-testmailx":
          Util.setProperty("SMTPServer", "mailx");
          SimpleSMTPThread thr = SimpleSMTPThread.email("dckump@gmail.com", "Mailx Test", "this is the body of the mailx test!!\nDave\n");
          if(thr.wasSuccessful()) {
            Util.prt("Your mailx email was sent to dckump@gmail.com !!");
          }
          else {
            Util.prt("Email failed to "+thr.getSendMailError());
          }
          System.exit(1);
        case "-b":
          filename=args[i+1];
          istart=i+2;
          break;
        case "-alt":
          alternate=true;
          istart=i+1;
          break;
        case "-bind":
          String [] parts = args[i+1].split(":");
          if(parts.length != 4) Util.prta("-bind does not have 4 arguments must be IP:IPalt:port:portAlt");
          else {
            SimpleSMTPThread.setBinding(parts[0],  parts[1], Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));
            Util.prta("Set Binding to "+parts[0]+" "+parts[1]+" "+parts[2]+" "+parts[3]);
          } i++;
          istart=i+1;
          break;
        case "-dbg":
          SimpleSMTPThread.setDebug(true);
          istart=i+1;
          break;
      }
    }
    if(subject != null) {
      StringBuilder sb = new StringBuilder(1000);
      sb.append("BEGIN\n");
      try {
        BufferedReader in;
        if(filename == null) in = new BufferedReader(new InputStreamReader(System.in));
        else in = new BufferedReader(new FileReader(filename));
        String line;
        while( (line = in.readLine()) != null) {
          sb.append(line).append("\n");
        }
        in.close();
        sb.append("END\n");
        for(int j=istart; j<args.length; j++) {

          SimpleSMTPThread thr;
          if(alternate) thr = SimpleSMTPThread.emailAlt(args[j], subject, sb.toString());
          else thr = SimpleSMTPThread.email(args[j], subject, sb.toString());
          thr.waitForDone();
          if(thr.wasSuccessful()) {
            Util.prt("Your email was sent to "+args[j]);
          }
          else {
            Util.prt("Email failed to "+args[j]+" "+thr.getSendMailError());
          }

        }
      }
      catch(IOException e) {
        Util.IOErrorPrint(e,"Email I/O error");
      }

      System.exit(0);
    }
    System.exit(0);
    Util.prt("SimpleSMTPThread test start");

  }
}


