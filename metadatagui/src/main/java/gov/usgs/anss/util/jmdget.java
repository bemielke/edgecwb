
/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.Socket;

/**
 * This class is a java version of the mdget C program for use on Windows and other non-*nix
 * systems. It is not dependent on any of the outside software. On systems with full EdgeCWB code
 * you can run a similar routine in class StaSrv with "java -cp $PATH_TO_BIN/cwblib.jar
 * gov.usgs.anss.util.Stasrv [args]".
 * <br>
 * This stand alone version is mostly for users who only need the lightweight clients.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class jmdget {

  private static final String mdgetHelp = "MDGET version 1.7 2017/08/01 def server=cwbpub.cr.usgs.gov port=2052\n"
          + "   Normal cooked response command (-cooked is assumed and optional) : \n"
          + "\n"
          + "   -s regexp where regexp is a regular expression for a FDSN/!sSEED channel in NNSSSSSCCCLL\n"
          + "   -a AGENCY.DEPLOY.STATION -coord is for IADSR coordinates where * can be used at the end on any portion\n"
          + "      ADSL only contains coordinates, descriptions, etc, but no seismometer responses\n"
          + "      A.D.S.L  The .L is optional.  An * can be at the end of any portion. Most useful 'FDSN.IR.*'\n"
          + "-s regexp [-b date][-e date|-d dur[d]][-xml][-u um|nm]\n"
          + "\n"
          + "       This is the '-cooked' response mode.  Get all matching channel epochs\n"
          + "       between the given dates and return in SAC format (default) or XML format\n"
          + "       in the displacement units specified (default is nm) for channels matching\n"
          + "       the regular expression.  Note : -cooked is the default command.\n"
          + "\n"
          + "  Other possible commands :\n"
          + "-alias [-s NNSSSSS|-a A.S.D.L]  return the aliases for the given station\n"
          + "-orient -s NNSSSSS [-b date][-e date][-d dur[d]][-xml] return coord/orientation epochs\n"
          + "-coord [-s NNSSSSS|-a A.S.D.L] [-b date][-e date][-d dur[d]][-xml][-kml] return coord/orientation epochs by channel\n"
          + "-lsc -s regexp Return a list of channels matching the regular expression\n"
          + "-desc [-s NNSSSSS|-a A.S.D.L]  return the NEIC long station name and operators \n"
          + "-resp[:dirstub] regexp get all RESP files and put in dirstub directory\n"
          + "-dataless[:dirstub] get all matching dataless seed volumes and put in dirstub\n"
          + "-station -s NNSSSSS [-xml] return all information about a station optionally in XML (no wildcards!)\n"
          + "-kml -s NNSSSSSSCCCLL KML file returned for all matching station\n"
          + "-icon NAME for -kml use this icon from http://earthquake.usgs.gov/eqcenter/shakemap/global/shake/icons/NAME.png\n"
          + "\n"
          + "  Notes on various options :\n"
          + "date     Dates can be of the form YYYY,DDD-hh:mm:ss or YYYY/MM/DD-hh:mm:ss\n"
          + "         if -b all is used, then all epoch from 1970 to present will be returned\n"
          + "         if -b and -e are omitted, they default to the current time\n"
          + "         if -b is present and -e is omitted, the end date will equal the begin date\n"
          + "regexp   To specify and exact channel match (12 char fixed field) use the full NNSSSSSCCCLL (use quotes!)\n"
          + "         For pattern matching '.' matches any single character, [ABC] for A, B, or C\n"
          + "         '.*' matches anything zero or more times e.g. US.* would match the US network\n"
          + "            always enclose '.*' in quotes since most shells give '*' other meanings\n"
          + "         [A-Z] allows any character A to Z inclusive in the position.\n"
          + "         A|B means matches A or B so 'US.*|AT.*|IU.*' matches anything in the US, AT or IU nets\n"
          + "         Examples : 'USDUG  [BL]H.00 matches network US, station DUG, BH? or LH? & loc 00\n"
          + "         US[A-C]....BHZ.. match and US station starting with A, B or C BHZ only & all loc\n"
          + "-delaz   [mindeg:]maxdeg:lat:long [-s regexp][-kml] return list of stations within deg of lat and long and option regexp \n"
          + "-delazc  [mindeg:]maxdeg:lat:long [-s regexp][-kml] return list of channels within deg of lat and long and option regexp \n"
          + "-allowbad Return results even if the matching metadata was marked bad because of unreasonable form - use at your own risk\n"
          + "-allowwarn Return results even if the matching metadata was marked suspect because of tests on input - use at your own risk!\n"
          + "-help    Get help message from server (for expert mode)\n"
          + "-u [um|nm] Units of responses are to be in nanometers or micrometers\n"
          + "-xml     Output response in XML format.  May work for other options (not normally needed)\n"
          + "-c [acdosr] pass command exactly (expert mode)\n"
          + "-sac[:dirstub] Output files in SAC format unit=um one cooked response per file line USOXF__BHZ00.sac.pz\n"
          + "-h nnn.nnn.nnn.nnn Use the given server rather than the default=cwbpub.cr.usgs.gov (cwbpub)\n"
          + "-p nnnn  Use port nnnn instead of the default server port number (2052)\n";
  private StringBuilder sb = new StringBuilder(100);
  public String getOutput() {return sb.toString();}
  public jmdget(String argsline) {
    String [] args = argsline.split("\\s");
    doIt(args);
  }
  /**
   * This class emulates the mdget C program .
   *
   * @param args
   */
  public jmdget(String[] args) {
    doIt(args);
  }
  private void doIt(String [] args) {
    if(sb.length() > 0) {
      sb.delete(0, sb.length());
    }
    StringBuilder sql = null;
    String host = null;
    int port = 2052;
    TimeoutSocketThread timeout;
    int ms = 30000;
    boolean dbg = false;
    boolean sbout = false;
    boolean kml = false;
    String dirstub = "./";
    boolean alias = false;
    for (int i = 0; i < args.length; i++) {
      if (sql != null) {
        sql.append(args[i]).append(" ");
        continue;
      }
      if (args[i].equals("-sql")) {
        sql = new StringBuilder(100);
        sql.append(args[i + 1]).append(" ");
        i++;
        continue;
      }
      else if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
      }
      else if (args[i].equals("-sb")) {
        sbout = true;
        
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-c")) {
        sb.append("-c ");
        char chr = (char) args[i + 1].charAt(1);
        sb.append(args[i + 1]).append(" ");
        if (!(chr == 'a' || chr == 'c' || chr == 'd' || chr == 'k'
                || chr == 'l' || chr == 'o' || chr == 's' || chr == 'r')) {
          System.out.println("-c arguments must be a, c, d, l, o, r, or s\n");
          System.exit(1);
        }
        i++;
      } else if (args[i].equals("-b") || args[i].equals("-e")
              || args[i].equals("-s")) {
        sb.append(args[i]).append(" ").append(args[i + 1].replaceAll(" ", "-")).append(" ");
        i++;
      } else if (args[i].equals("-help") || args[i].equals("-?")) {
        System.out.print(mdgetHelp);
        return;
      } else if (args[i].equals("-station")) {
        sb.append("-c s ");
      } else if (args[i].equals("-cooked")) {
        sb.append("-c r ");
      } else if (args[i].equals("-orient")) {
        sb.append("-c o ");
      } else if (args[i].equals("-coord")) {
        sb.append("-c c ");
      } else if (args[i].equals("-lsc")) {
        sb.append("-c l ");
      } else if (args[i].equals("-desc")) {
        sb.append("-c d ");
      } else if (args[i].equals("-kml")) {
        sb.append("-c k ");
        kml = true;
      } else if (args[i].equals("-alias")) {
        sb.append("-c a ");
        alias = true;
      } else if (args[i].contains("-dataless")) {
        sb.append("-c r -dataless ");
        if (args[i].contains(":")) {
          dirstub = args[i].substring(args[i].indexOf(":") + 1);
        }

      } else if (args[i].contains("-sac")) {
        sb.append("-sac ");
        if (args[i].contains(":")) {
          dirstub = args[i].substring(args[i].indexOf(":") + 1);
        }
      } else if (args[i].contains("-resp")) {
        sb.append("-c r -resp ");
        if (args[i].contains(":")) {
          dirstub = args[i].substring(args[i].indexOf(":") + 1);
        }
      } else if (args[i].equals("-xml")
              || args[i].equals("-allowwarn") || args[i].equals("-allowbad")
              || args[i].equals("-forceupdate")) {
        sb.append(args[i]).append(" ");
      } else if (args[i].equals("-d") || args[i].equals("-icon")
              || args[i].equals("-u") || args[i].equals("-a")
              || args[i].equals("-delaz") || args[i].equals("-delazc")) {
        sb.append(args[i]).append(" ").append(args[i + 1]).append(" ");
        if (args[i].equals("-a") && !alias) {
          sb.append("-c c ");
        }
        i++;
      }
      else if (args[i].equals("-test")) {
        
      } else {
        System.out.println("Bad argument at " + i + " args=" + args[i]);
        System.exit(3);
      }
    }
    /* if no -c has been selected by the arguments, default to get responses */
    if (sb.indexOf("-c") < 0) {
      sb.append("-c r ");
    }
    sb.append("\n");
    String h = host;
    if (h == null) {
      h = System.getProperty("metadataserver");
    }
    if (h == null) {
      h = "cwbpub.cr.usgs.gov";     // USGS public metadata server
    }
    if (h.equals("")) {
      h = "cwbpub.cr.usgs.gov";
    }
    host = h;
    if (port <= 0) {
      port = 2052;
    }
    Socket s = null;
    byte[] b = new byte[1000];
    timeout = new TimeoutSocketThread(host + "/" + port, s, ms);
    timeout.enable(false);

    //for(int i=0; i<args.length; i++) sb.append(args[i]).append(i == args.length-1?"\n":" ");
    // Try the request twice before giving up.
    for (int loop = 0; loop < 15; loop++) {
      try {
        if (s == null || s.isClosed() || !s.isConnected()) {
          //.out.println("StaSrv: Create new socket as it is null, closed of not connected! "+host+"/"+port);
          s = new Socket(host, port);
          timeout.setSocket(s);
        }
        timeout.enable(true);
        for (int i = 0; i < sb.length(); i++) {
          b[i] = (byte) sb.charAt(i);
        }
        s.getOutputStream().write(b, 0, sb.length());
        sb.delete(0, sb.length());
        int off = 0;
        int l;
        try {
          while ((l = s.getInputStream().read(b, 0, 1000)) >= 0) {
            if (l == 0) {
              break;
            }
            off += l;
            for (int i = 0; i < l; i++) {
              sb.append((char) b[i]);
            }
            //System.out.println("read="+l+" off="+off);
            //if(b[l-1] == 10 && b[l-2] == 10) break;
            if (sb.indexOf("<EOR>\n") >= 0) {
              break;
            }
          }
          //s.close();
          //s=null;
          timeout.enable(false);
          if(!sbout) System.out.print(sb);
          break;
        } catch (IOException e) {
          System.out.println("jmdget: Error reading response" + e.getMessage());
          try {
            s.close();
          } catch (IOException e2) {
          }
          s = null;
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e2) {
          }
        }
      } catch (IOException e) {
        System.out.println("jmdget: IOException setting up socket to " + host + "/" + port + " loop=" + loop + "/15 " + e.getMessage());
        if (s != null) {
          try {
            s.close();
          } catch (IOException e2) {
          }
        }
        s = null;
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e2) {
        }
        if (e.toString().contains("Connection timed out")) {
          break;
        }// Marked timed out
      }
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.print(mdgetHelp);
    } else {
      for(int i=0; i<args.length;i++) {
        if( args[i].equals("-test")) {
          String argline="";
          for (int j=i+1; j<args.length; j++) {
            argline += " "+args[j];
          }
          jmdget obj = new jmdget(argline.trim());
          System.out.println(obj.getOutput());
          System.exit(0);
        }
      }
      jmdget obj = new jmdget(args);
      System.out.println(obj.getOutput());
    }
    System.exit(0);
  }

  /**
   * Class is a time out thread which can be set up with some other thread to cause a socket to be
   * closed after some interval. The thread can be enabled and disabled. The currently set socket
   * can be changed in setSocket. If the socket is enable, then the user should call resetTimeout()
   * to reset the one shot interval.
   * <p>
   * Use to cause clean up of threads that can hang indefinitely on dead sockets.
   *
   * @author davidketchum
   */
  final class TimeoutSocketThread extends Thread {

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
    public void setInterval(long ms) {
      msInterval = ms;
    }

    /**
     * if set true, this timeout is active and it will loop close the socket in interval time
     *
     * @param t If true, disable timeout for now, if false, it is now enabled
     */
    public void enable(boolean t) {
      last = System.currentTimeMillis();
      enabled = t;
      interruptSent = false;
    }

    /**
     * set the socket to this new one
     *
     * @param s The new socket to monitor
     *
     */
    public void setSocket(Socket s) {
      target = s;
      interruptSent = false;
    }

    /**
     * Creates a new instance of TimeoutThread
     *
     * @param tg A string tag to use to refer to this thread
     * @param s The socket to close on timeout
     * @param interval The time in MS to set the watchdog for sending an interrupt()
     */
    public TimeoutSocketThread(String tg, Socket s, int interval) {
      msInterval = interval;
      target = s;
      tag = tg;
      resetTimeout();
      interruptSent = false;
      terminate = false;
      enabled = false;
      this.setDaemon(true);
      start();
    }

    @Override
    public String toString() {
      return "isEnabled=" + enabled + " " + target;
    }

    /**
     * loop and if enabled, wait the timeout interval and close the socket
     *
     */
    @Override
    public void run() {
      while (!terminate) {
        while (!enabled || target == null) {
          try {
            sleep(100);
          } catch (InterruptedException e) {
          }
        }
        last = System.currentTimeMillis();
        /* start the interval, we must be enabled */
        while ((System.currentTimeMillis() - last) < msInterval) {
          try {
            sleep(Math.max(msInterval - (System.currentTimeMillis() - last), 1));
          } catch (InterruptedException e) {
            if (terminate) {
              break;
            }
          }
        }
        // if we get here the timer has expired.  Terminate the target
        // if this thread has it terminate set, skip out!
        if (!enabled || target == null) {
          continue;  // we have to be enabled and have something to close!
        }
        if (!target.isClosed() && !terminate) {    // if its already closed or we are terminating, skip this
          interruptSent = true;
          System.out.print(tag + " TSO: ******** timed out close socket s=" + target);
          if (target != null) {
            try {
              if (!target.isClosed()) {
                target.close();
              }
            } catch (IOException e) {
              System.out.println(tag + "TSO: IO error on close=" + e);
            }
          }
        }
      }
      System.out.println(tag + " TSO: exiting terminate=" + terminate);
    }

    /**
     * has this timeout expired and sent an interrupted
     *
     * @return has an interrupt be sent by this timeout thread
     */
    public boolean hasSentInterrupt() {
      return interruptSent;
    }

    /**
     * reset the time out interval (basically a watchdog reset)
     */
    public final void resetTimeout() {
      last = System.currentTimeMillis();
    }

    /**
     * shutdown this thread
     */
    public void shutdown() {
      terminate = true;
      interrupt();
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }
  }

}
