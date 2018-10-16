/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.net;
import gov.usgs.anss.util.*;
import java.io.IOException;
import java.net.Socket;

/** Class is a time out thread which can be set up with some other thread to cause
 * a socket to be closed after some interval.  The thread can be enabled and disabled.
 * The currently set socket can be changed in setSocket.  If the socket is enable,
 * then the user should call resetTimeout() to reset the one shot interval.
 * <p>
 * Use to cause clean up of threads that can hang indefinitely on dead sockets.
 *
 * @author davidketchum
 */
public class TimeoutSocketThread extends Thread {
  boolean enabled;
  long last;        // Last time timeout watchdog hit
  long msInterval;
  Socket target;    // This is the thread we are to stop if it times out
  boolean interruptSent;
  boolean terminate;
  String tag;
  /**
   * 
   * @param ms Set the timeout interval to this value.
   */
  public void setInterval(long ms) {msInterval=ms;}
  /** if set true, this timeout is active and it will loop close the socket in interval time
   *  @param t If true, disable timeout for now, if false, it is now enabled
   */
  public void enable(boolean t) {last = System.currentTimeMillis();enabled=t;interruptSent=false;}
  /** set the socket to this new one
   * @param s The new socket to monitor
   *
   */
  public void setSocket(Socket s) {target=s;interruptSent=false;}
  /** Creates a new instance of TimeoutThread
   *@param tg A string tag to use to refer to this thread
   * @param s The socket to close on timeout
   * @param interval The time in MS to set the watchdog for sending an interrupt()
   */
  public TimeoutSocketThread(String tg, Socket s, int interval) {
    msInterval = interval;
    target = s;
    tag = tg;
    resetTimeout();
    interruptSent=false;
    terminate=false;
    enabled=false;
    this.setDaemon(true);
    start();
  }
  @Override
  public String toString() {return "isEnabled="+enabled+" "+target;}
  /** loop and if enabled, wait the timeout interval and close the socket
   * 
   */
  @Override
  public void run() {
    while(!terminate) {
      while(!enabled || target == null)  {try{sleep(100);} catch(InterruptedException e) {}}
      last = System.currentTimeMillis();      /* start the interval, we must be enabled */
      while((System.currentTimeMillis() - last) < msInterval) {
        try {
          sleep(Math.max(msInterval - (System.currentTimeMillis() - last),1));
        } catch(InterruptedException e) {if(terminate) break;}
      }
      // if we get here the timer has expired.  Terminate the target
      // if this thread has it terminate set, skip out!
      if(!enabled || target == null) continue;  // we have to be enabled and have something to close!
      if(!target.isClosed() && !terminate) {    // if its already closed or we are terminating, skip this
        interruptSent=true;
        Util.prta(tag+" TSO: ******** timed out close socket s="+target);
        if(target != null)
          try {
            if(!target.isClosed()) target.close();
          }
          catch(IOException e) {
            Util.prt(tag+"TSO: IO error on close="+e);
          }
      }
    }
    Util.prta(tag+" TSO: exiting terminate="+terminate);
  }
  /** has this timeout expired and sent an interrupted
   *@return has an interrupt be sent by this timeout thread
   */
  public boolean hasSentInterrupt() {return interruptSent;}
  /** reset the time out interval (basically a watchdog reset)
   */
  public final void resetTimeout() {
    last = System.currentTimeMillis();
  }
    /** shutdown this thread
     */
    public void shutdown() {terminate=true; interrupt();}
    public void terminate() {terminate=true; interrupt();}
}
