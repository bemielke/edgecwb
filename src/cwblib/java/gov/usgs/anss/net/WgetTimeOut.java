/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.anss.net;
import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import gov.usgs.anss.util.*;
public class WgetTimeOut {
  String commandName ;
  int count;
  boolean dbg=true;
  URLConnection url;
  byte [] out;
  long nbyte;
  String tag="";
  WGTimeout timeout;
  int state;
  InputStream in ;
  boolean IOErrorDuringFetch;
  int connectFails;
  boolean terminate;
  public void terminate(boolean b) {terminate=true;}
  public int getLength() {return (int) nbyte;}
  public byte[] getBuf() {return out;}
  public int getState() {return state;}
  public WgetTimeOut(String command) {
    commandName=command;
    try {
      timeout = new WGTimeout(this);
      state=1;
      url = (new URL(command)).openConnection();
      state=2;
      state=4;
      if(dbg) printHeader(url);
      if(url instanceof HttpURLConnection) {
        state=5;
        readHttpURL( (HttpURLConnection) url);
      }
      else {
        state=6;
        readURL(url.getInputStream());
      }
      state=7;
    }
    catch(java.net.MalformedURLException e) {
      Util.prta(tag+" ** Malformed URL = command");
    }
    catch(java.io.IOException e) {
      Util.prta(tag+" ** IOError thrown e="+e.getMessage());
      if(e.getMessage().contains("onnection reset") || e.getMessage().contains("onnection timed out") ) {
        connectFails++;
      }
    }
    state=8;
    timeout.terminate();
  }

  /** read up to the number of bytes, or throw exception.  Suitable for sockets since the read method
   * uses a lot of CPU if you just call it.  This checks to make sure there is data before attempting the read.
   *@param in The InputStream to read from
   *@param buf The byte buffer to receive the data
   *@param off The offset into the buffer to start the read
   *@param len Then desired # of bytes
   * @return The length of the read in bytes, zero if EOF is reached
   * @throws IOException if one is thrown by the InputStream
   */
  public int socketRead(InputStream in, byte [] buf, int off, int len) throws IOException {
    int nchar= in.read(buf, off, len);// get nchar
    if(nchar <= 0) {
      Util.prta(len+" SR read nchar="+nchar+" len="+len+" in.avail="+in.available());
      return 0;
    }
    return nchar;
  }
  private void readURL(InputStream input) throws IOException {
    in = input;
    long nextLog=1000000;
    try {
      while (!terminate) {
        int nb = socketRead(in, out, (int) nbyte, Math.min(10000, out.length - (int) nbyte));
        state=58;
        //int nb = in.read(out, nbyte, Math.min(20000, out.length - nbyte));
        if(nb <= 0) {
          //if(dbg)
          Util.prta(tag+commandName +
              ": EOF on Read " + nbyte +
              " bytes from " + url.getURL());
          break;
        }
        //else Util.prta("nb="+nb+" total="+(nbyte+nb));
        state=59;
        nbyte += nb;
        if(nbyte > nextLog) {
          Util.prta(nbyte+" bytes read of "+url.getContentLength()+" "+(nbyte*100/url.getContentLength())+"%");
          nextLog += 1000000;
        }
        if(nbyte >= out.length) {
          byte [] temp = new byte[out.length*3/2];
          System.arraycopy(out, 0, temp, 0, (int) nbyte);
          out=temp;
        }
      }
    } catch (EOFException e) {
      IOErrorDuringFetch=true;
      if(dbg) Util.prta(tag+commandName +
              ": Read " + nbyte +
              " bytes from " + url.getURL());
    } catch (IOException e) {
      IOErrorDuringFetch=true;
      Util.prta(tag+"IOError:"+ e + ": " + e.getMessage());
      if(dbg) Util.prt(tag+commandName +
              ": Read " + count +
              " bytes from " + url.getURL());
    }
    timeout.terminate();
  }

  private void readHttpURL(HttpURLConnection url)
    throws IOException {

    long before, after;

    //url.setAllowUserInteraction (true);
    if(dbg) Util.prt(tag+commandName + ": Contacting the URL ...");
    url.setConnectTimeout(20000);
    url.setReadTimeout(30000);
    state=51;
    url.connect();
    if(dbg) Util.prt(tag+commandName + ": Connect. Waiting for reply ...");
    before = System.currentTimeMillis();
    in = url.getInputStream();
    after = System.currentTimeMillis();
    if(dbg) Util.prt(tag+commandName + ": The reply takes " +
            ((int) (after - before) / 1000) + " seconds");

    before = System.currentTimeMillis();
    state=52;

    try {
      if (url.getResponseCode() != HttpURLConnection.HTTP_OK) {
        Util.prt(tag+commandName + ":: " + url.getResponseMessage());
      } else {
        if(dbg) printHeader(url);
        out = new byte[url.getContentLength()+1000];

        state=53;
        readURL(in);
        state=54;
      }
    } catch (EOFException e) {
      state=55;
      after = System.currentTimeMillis();
      int milliSeconds = (int) (after-before);
      if(dbg) Util.prt(tag+commandName +
              ": Read " + count +
              " bytes from " + url.getURL());
      if(dbg) Util.prt(tag+commandName + ": HTTP/1.0 " + url.getResponseCode() +
              " " + url.getResponseMessage());
      url.disconnect();

      Util.prt(tag+commandName + ": It takes " + (milliSeconds/1000) +
              " seconds" + " (at " + round(count/(float) milliSeconds) +
              " K/sec).");
      if (url.usingProxy()) {
        if(dbg) Util.prt(tag+commandName + ": This URL uses a proxy");
      }
    } catch (IOException e) {
      state=56;
      Util.prt( e + ": " + e.getMessage());
      if(dbg) Util.prt(tag+commandName +
              ": I/O Error : Read " + count +
              " bytes from " + url.getURL());
      Util.prt(tag+commandName + ": I/O Error " + url.getResponseMessage());
      IOErrorDuringFetch=true;
    }
    state=57;
    //System.exit(0);
  }


  public float round(float f) {
    return Math.round(f * 100) / (float) 100;
  }


  private void printHeader(URLConnection url) {
    Util.prt(tag+": Content-Length   : " +
            url.getContentLength() );
    Util.prt(tag+ ": Content-Type     : " +
            url.getContentType() );
    if (url.getContentEncoding() != null)
      Util.prt(tag+": Content-Encoding : " +
              url.getContentEncoding() );
  }
  public void close() {
    Util.prta(tag+" **** WGet close() started state="+state+" in="+in);
    if(timeout != null) timeout.terminate();
    try {
      if(in != null) in.close();
    }
    catch(IOException e) {
      e.printStackTrace();
    }
    if(url == null) {
      Util.prta(" ***** the URL is hung opening the connection.  abort thread!");
      terminate=true;
    }
    else {

      Util.prta(tag+" ***** WGet close() close via url.getInputStream()");
      try{url.getInputStream().close();}
      catch(IOException e) {
        e.printStackTrace();

      }
    }

    Util.prta(tag+" **** WGet close() completed url="+url);
  }
  class WGTimeout extends Thread {
    int lastnbyte;
    WgetTimeOut thr;
    boolean terminate;
    public void terminate() {terminate=true; interrupt();}
    public WGTimeout(WgetTimeOut thread) {
      thr=thread;
      start();
    }
    @Override
    public void run() {
      lastnbyte = thr.getLength();
      int i = 1;
      boolean timedOut=false;
      while(!terminate) {
        // Wait 2 seconds to check for terminates, wait 60 seconds to check on progress
        // and close socket, if two sucessive timeouts occur SendEvent to warn it may be hung
        try{sleep(2000);} catch(InterruptedException e) {}
        if(terminate) break;
        if(i++ % 30 == 0) {
          if(lastnbyte == thr.getLength()) {
            Util.prta(" *** "+tag+" WgetTimeout: has timed out.  Close socket state="+thr.getState());
            thr.close();
            timedOut=true;
          }
          else timedOut=false;
          lastnbyte = thr.getLength();
        }
      }
      Util.prta(tag+" WgetTimeout has  terminated");
    }
  }
  }