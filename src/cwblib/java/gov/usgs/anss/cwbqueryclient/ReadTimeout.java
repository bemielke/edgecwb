/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.Socket;

/** This class implements a timeout on a socket with a minimum time, if the user does not reset
 * this class before the timeout interval expires, the socket is closed.
 *
 * @author davidketchum
 */
public final class ReadTimeout extends Thread {

  private Socket socket;
  private long lastTime;
  private boolean exit;
  private final long waitLen;
  private final String tag;
  private int loop;

  public ReadTimeout(Socket ds, int timeout) {
    socket = ds;
    tag = this.getId() + ":";
    lastTime = System.currentTimeMillis();
    waitLen = timeout * 1000L;
    //Util.prta(tag+"New ReadTimeout wait="+waitLen);
    start();
  }

  public void setSocket(Socket ds) {
    socket = ds;
  }

  public void reset() {
    lastTime = System.currentTimeMillis();
  }

  public void terminate() {
    exit = true;
  }

  @Override
  public void run() {
    for (;;) {
      loop++;
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      if (exit) {
        break;
      }
      if (socket != null) {
        if (System.currentTimeMillis() - lastTime > waitLen) {
          Util.prta(getName() + " ReadTimeout went off. close the socket "
                  + (System.currentTimeMillis() - lastTime) + " waitlen=" + waitLen
                  + " sock.isCLosed()=" + (socket == null ? "Null" : socket.isClosed()) + " loop=" + loop);
          if (socket != null) {
            //if(socket.isClosed()) break;
            try {
              socket.close();
            } catch (IOException expected) {
            }
            socket = null;
          }
          //break;
        }
      }
    }
    if (!exit) {
      Util.prt(getName() + " ReadTimeout exited=" + exit);
    }
  }
}
