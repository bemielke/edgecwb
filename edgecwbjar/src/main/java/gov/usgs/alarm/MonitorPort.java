/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.io.EOFException;
import static java.lang.Thread.sleep;
import java.net.Socket;

/**
 * This class monitors services from a QueryMom, EdgeMom or any other process
 * that provide a MonitorServer with key value pairs. The argument line is like
 * :
 * <br>
 * host:port[:process[:account[:action]]]
 * <br>
 * 123.123.123.123:7800:EdgeMom:vdl:alarm
 * <br>
 * <PRE>
 * argument        description
 * host   ip.adr  The IP address or DNS translatable name of the host to monitor
 * port   nnnn    The port number that some monitor service is running on, for (EdgeMom 7800-7809 based on instance number)
 * process name   The process name like 'EdgeMom' or 'QueryMom'
 * account name   The account the process is running in like vdl
 * action  name   Only 'alarm' is defined right now and this send an event to the alarm system
 *
 * </PRE> Other actions are anticipated like triggering a "kill" on the process
 * on the remote computer through the command system
 *
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class MonitorPort extends Thread {

  private final int processport;
  private final String host;
  private final String processName;
  private final String processAccount;
  private final String action;
  private final EdgeThread par;
  private boolean dbg;
  private final String tag;
  private boolean terminate;

  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   *
   * @param s A string to print to the log
   */
  protected void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  /**
   *
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  protected void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public MonitorPort(String argline, EdgeThread parent) {
    String[] args = argline.split(":");

    par = parent;
    if (args.length >= 3) {
      processName = args[2];
    } else {
      processName = "UnknProc";
    }
    if (args.length >= 4) {
      processAccount = args[3];
    } else {
      processAccount = "UnknAcct";
    }
    if (args.length >= 5) {
      action = args[4].toLowerCase();
    } else {
      action = "alarm";
    }
    if (args.length >= 2) {
      host = args[0];
      processport = Integer.parseInt(args[1]);
    } else {
      prta("MonitorPort must have at least two colon separated arguments host:port[:process[:account[:action]]]");
      host = "bad";
      processport = -1;
      tag = "";
      return;
    }
    setDaemon(true);
    tag = "[MP:" + host + "/" + processport + "/" + processName + "/" + processAccount + "]";
    prta(tag + "Starting");
    start();
  }

  @Override
  public void run() {
    StringBuilder msg = new StringBuilder(2000);
    //long now;
    try {
      sleep(Math.abs(tag.hashCode() % 30000));
    } catch (InterruptedException expected) {
    }     // randomize start time
    byte[] buf = new byte[2048];
    int nbad = 0;
    int len;
    int offset;
    long loop = 0;
    while (!terminate) {
      boolean ok = false;
      try {
        if (dbg) {
          Util.prta(tag + " open socket");
        }
        try (Socket s = new Socket(host, processport)) {
          s.setSoTimeout(120000);
          if (dbg) {
            Util.prta(tag + " Socket opened as s=" + s);
          }
          Util.clear(msg);
          offset = 0;
          while ((len = Util.socketRead(s.getInputStream(), buf, offset, buf.length - offset)) > 0) {
            if (dbg) {
              Util.prt(tag + " read len=" + len + " offset=" + offset);
            }
            offset += len;
            if (offset >= buf.length) {
              Util.prt(tag + " ** msg length > " + buf.length + " bytes.  Increase buffer size");
              byte[] tmp = new byte[buf.length * 2];
              System.arraycopy(buf, 0, tmp, 0, buf.length);
              buf = tmp;
            }
          }
          len = offset;
          if (dbg) {
            if (len > 0) {
              for (int i = 0; i < len; i++) {
                msg.append((char) buf[i]);
              }
            }
            Util.prta(tag + "len=" + len + " msg=" + msg);
          }
          if (loop++ % 20 == 0) {
            Util.prta(tag + "len=" + len);
          }
          if (len > 10) {
            nbad = 0;
            ok = true;
          } else {
            nbad++;     // if the message is >10, everything is ok, else increment bad in a row
          }
        }
        // If we have gon
        if (!ok && action.equals("alarm")) {
          SendEvent.edgeEvent("MonPortFail", tag + " is not responding len=" + len, ok);
        }
        if (!ok & nbad % 30 == 2) {
          if (action.equals("alarm")) ; // We already sent a event - let alarm do its thing
          else {
            Util.prt(tag + " *** action=" + action + " is undefined!");
          }
        }
      } catch (EOFException e) {
        Util.prt(tag + "EOF Error!!!!");
        //e.printStackTrace();
      } catch (IOException e) {
        SendEvent.edgeSMEEvent("MonPortErr", tag + " e=" + (e.getMessage() == null ? e : e.getMessage()), this);
        Util.prta(tag + " had an IOError e=" + e);
        //e.printStackTrace();
      }
      try {
        sleep(60000);
      } catch (InterruptedException expected) {
      }
    }   // end of infinite loop
    prta(tag + " Is exiting");
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    if (args.length == 0) {
      args = new String[1];
      args[0] = "127.0.0.1:7800:EdgeMom:vdl:alarm";
    }
    MonitorPort mon = new MonitorPort(args[0], null);
    mon.setDebug(true);
    for (;;) {
      Util.sleep(1000);
    }
  }
}
