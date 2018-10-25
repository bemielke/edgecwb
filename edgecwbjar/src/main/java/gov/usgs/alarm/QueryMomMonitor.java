/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.waveserver.WaveServerClient;
import gov.usgs.anss.waveserver.MenuItem;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import java.io.IOException;
import java.net.Socket;
import java.util.GregorianCalendar;
import java.util.ArrayList;

/**
 * This class monitors services from a QueryMom - QueryServer, CWBWaveServer -
 * It needs to know the IP of the Server, the ports for the two services, and a
 * list of channels to use in testing. If the cwbwsport is zero, no testing of
 * this function is done. The query interval is normally from 65 seconds in the
 * past to 60 seconds in the past which works well for BH and HH data. The delay
 * secs for each port can be lengthened for lower sample rate data so that only
 * one or two packets of response are returned on each query (for 1 second data
 * the delay seconds should be about 600 seconds).
 * <br>
 * ipadr:cwbwsport[/delaysec]:queryport[/delaysec]:SEEDNAMEdashes:seedname2:seedname3:....
 * <br>
 * example: localhost:2060:2061/600:USDUG--BHZ00:ATPMR--BHZ--:GTLPAZ-BHZ00
 * <br>
 * This would allow the last 600 seconds in the query (useful for slow
 * channels). Note the seednames must be 12 characters long following the
 * NNSSSSSCCCLL convention with dashes for spaces.
 * <PRE>
 * field    value     Description
 * ipadr   ip.adr    Address of DNS resolvable name.  An exclamation point in the address turns on debugging.
 * cwbsport nnnn     The port of the CWBWaveServer (default delay=65 seconds)
 * queryport nnnn    The port of the MiniSeed QueryServer (default delay=65 seconds)
 * SEEDNAME NNSSSSSCCCLL  The 12 character seedname with hyphens for spaces
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class QueryMomMonitor extends Thread {

  private final int cwbwsport, queryport;
  private final String host;
  private final String[] channels;
  private WaveServerClient cwbws;
  private long wsdelay = 65000;
  private long cwbdelay = 65000;
  private final EdgeThread par;
  private boolean dbg;
  private final String tag;

  /**
   *
   * @param s A string to print to the log
   */
  protected final void prt(String s) {
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

  /**
   *
   * @param s A string to print to the log
   */
  protected final void prt(StringBuilder s) {
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
  protected final void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   *
   * @param argline a
   * ipadr:cwbwsport:queryport:NNSSSS-CCCLL:seedname2:seedname3:.... command
   * line
   * @param parent The logging process, if null, logging will go to the default
   * log
   */
  public QueryMomMonitor(String argline, EdgeThread parent) {
    String[] args = argline.split(":");
    par = parent;
    if (args[0].contains("!")) {
      dbg = true;
      host = args[0].replaceAll("!", "");
    } // if !, then turn on debugging
    else {
      host = args[0];
    }
    prta("QMM: startup argline=" + argline + " args.length=" + args.length);
    if (args[1].contains("/")) {
      String[] parts = args[1].split("/");
      cwbwsport = Integer.parseInt(parts[0]);
      wsdelay = Integer.parseInt(parts[1]) * 1000;
      prta("QMM: got slash in 2nd arg " + args[1] + " parts.length=" + parts.length + " wsdelay=" + wsdelay + " " + (parts.length > 1 ? parts[1] : ""));
    } else {
      cwbwsport = Integer.parseInt(args[1]);
    }
    if (args[2].contains("/")) {
      String[] parts = args[2].split("/");
      queryport = Integer.parseInt(parts[0]);
      cwbdelay = Integer.parseInt(parts[1]) * 1000;
      prta("QMM: got slash in 3rd arg " + args[2] + " parts.length=" + parts.length + " cwbdelay=" + cwbdelay + " " + (parts.length > 1 ? parts[1] : ""));
    } else {
      queryport = Integer.parseInt(args[2]);
    }
    channels = new String[args.length - 3];
    for (int i = 3; i < args.length; i++) {
      channels[i - 3] = args[i].replaceAll("-", " ");
    }
    if (cwbwsport > 0) {
      cwbws = new WaveServerClient(host, cwbwsport); // this does not actually open the port until a command is issued.
    } else {
      cwbws = null;
    }
    setDaemon(true);
    tag = "QMM:[" + host + "/" + cwbwsport + "/" + queryport + "]";
    start();
  }

  @Override
  public void run() {
    GregorianCalendar start = new GregorianCalendar();
    GregorianCalendar end = new GregorianCalendar();
    long now;
    try {
      sleep(Math.abs(tag.hashCode() % 30000));
    } catch (InterruptedException expected) {
    }     // randomize start time
    ArrayList<TraceBuf> tbs = new ArrayList<>(10);
    byte[] version = "VERSION: QMM\n".getBytes();
    byte[] buf = new byte[512];
    boolean okscnl = true;
    boolean okmenu = true;
    int waitMS = 120000;
    try {
      sleep(waitMS);
    } catch (InterruptedException expected) {
    }    // sleep while querymom comes up if this is a general restart
    prta(tag + " starting cwbws=" + cwbws + " cwb=" + host + "/" + queryport + "/" + cwbdelay + " ws=" + cwbwsport + "/" + wsdelay);
    for (;;) {
      try {
        now = System.currentTimeMillis();

        if (now % 86400000L < 300000) {
          prta(tag + "Need to wait for new day to get settled");
          try {
            sleep(300000 - now % 86400000L);
          } catch (InterruptedException expected) {
          }
          now = System.currentTimeMillis();
        }
        if (cwbws != null && cwbwsport > 0) {
          okscnl = false;
          okmenu = false;
          try {
            try (Socket s = new Socket(host, cwbwsport)) {
              s.setSoTimeout(10000);
              s.getOutputStream().write(version);
              try {
                sleep(1000);
              } catch (InterruptedException expected) {
              }
              int l = s.getInputStream().read(buf, 0, Math.min(512, s.getInputStream().available()));
              if (dbg) {
                prta(tag + " VERSION command read = " + new String(buf, 0, l));
              }
            }
          } catch (IOException e) {
            handleIOError(e);
            try {
              sleep(120000);
            } catch (InterruptedException expected) {
            }
            continue;
          }
          for (String channel : channels) {
            try {
              MenuItem item = cwbws.getMENUSCNL("QMM1", channel);
              if (item != null) {
                if (dbg) {
                  prta(tag + " " + host + " getMenu for " + channel + "=" + item);
                }
                if (item.getSeedname() != null) {
                  if (item.getSeedname().trim().equals(channel)
                          && now - item.getEndInMillis() < 120000) {
                    okmenu = true;
                    break;
                  }
                } else if (dbg) {
                  prta(tag + " " + host + " getMenu failed");
                }
              }
              prta(tag + " " + host + " ** getMenu returned null for " + channel);
            } catch (IOException e) {
              handleIOError(e);
              cwbws.closeSocket();
              cwbws = new WaveServerClient(host, cwbwsport);
              if (par == null) {
                e.printStackTrace();
              } else {
                e.printStackTrace(par.getPrintStream());
              }
            }
          }
          for (String channel : channels) {
            try {
              start.setTimeInMillis(now - wsdelay);
              end.setTimeInMillis(now - 60000);
              cwbws.getSCNLRAW("QMM2", channel, start, end, true, tbs);
              if (dbg) {
                prta(tag + " GetSCNLRAW for " + channel + " " + Util.ascdatetime2(start)
                        + " returned " + tbs.size() + " " + (tbs.size() > 0 ? tbs.get(0).toString() : ":"));
              }
              if (tbs.size() > 0) {
                okscnl = true;
              } else {
                prta(tag + " *** GETSCNLRAW for " + channel + " " + Util.ascdatetime2(start) + " returned " + tbs.size());
              }
              cwbws.freeMemory(tbs);
              tbs.clear();
              if (okscnl) {
                break;
              }
            } catch (IOException e) {
              handleIOError(e);
              cwbws.closeSocket();
              cwbws = new WaveServerClient(host, cwbwsport);
              if (par == null) {
                e.printStackTrace();
              } else {
                e.printStackTrace(par.getPrintStream());
              }
            }
          }
        }
        if (!okscnl) {
          SendEvent.edgeSMEEvent("CWBWSNotCurr", tag + " CWBWaveServer does not have current data", this);
        }

        // Try QueryServer services
        boolean okquery = false;
        for (String channel : channels) {
          start.setTimeInMillis(now - cwbdelay);
          end.setTimeInMillis(now - 60000);
          String s = "-b " + Util.stringBuilderReplaceAll(Util.ascdatetime2(start), ' ', '-').substring(0, 19) + " -h " + host
                  + " -t null -uf -d 10 -s " + channel.replaceAll(" ", "-");
          ArrayList<ArrayList<MiniSeed>> mss = EdgeQueryClient.query(s);

          if (mss == null) {
            prta(tag + " ** error return from CWBQuery for " + s);
            SendEvent.edgeSMEEvent("QuerySrvErr", tag + " CWBQuery returned a null!", this);
          } else if (mss.size() > 0) {
            if (mss.get(0).size() > 0) {
              okquery = true;
              if (dbg) {
                prta(tag + " CWBQuery ok sz=" + mss.get(0).size() + " " + mss.get(0).get(0).toStringBuilder(null, 50));
              }
              EdgeQueryClient.freeQueryBlocks(mss, "QMM", (par == null ? null : par.getPrintStream()));
              break;
            }
          } else if (mss.size() <= 0) {
            prta(tag + " ** CWBQuery not o.k.  mss.size()=0 " + channel + " "
                    + Util.ascdatetime2(start) + " " + Util.ascdatetime2(end));
          } else {
            prta(tag + " ** CWBQuery no o.k. size=" + mss.get(0).size() + " " + channel + " "
                    + Util.ascdatetime2(start) + " " + Util.ascdatetime2(end));
          }
        }
        if (!okquery) {
          SendEvent.edgeSMEEvent("QueSrvNotCur", tag + " QueryServer does not have current data", this);
        }
        if (!okmenu || !okscnl || !okquery) {
          prta(tag + " *** QueryMom bad menubad=" + !okmenu
                  + " scnlnotcurr=" + !okscnl + " querynotcurr=" + !okquery);
          SendEvent.edgeSMEEvent("QueryMomBad", tag + " menu bad=" + !okmenu + " scnlbad=" + !okscnl + " query=" + !okquery, this);
        }
        if (dbg) {
          prta(tag + " menu bad=" + !okmenu + " scnlbad=" + !okscnl + " query=" + !okquery);
        }
        try {
          sleep(waitMS);
        } catch (InterruptedException expected) {
        }
      } catch (RuntimeException e) {
        prta(tag + " *** got runtime error.  continue e=" + e);
        e.printStackTrace(par.getPrintStream());
        SendEvent.edgeSMEEvent("RuntimeExcp", "QMM e=" + e, this);
      }
    }   // end of infinite loop
  }

  private void handleIOError(IOException e) {
    String err = e.toString();
    boolean timedOut = cwbws.timedOut();
    if (err.contains("Connection refused")
            || err.contains("Connection timed out")
            || err.contains("o route to host")) {
      if (err.contains("Connection refused")) {
        err = "refused";
      } else if (err.contains("timed out")) {
        err = "Conn timed out";
      } else if (err.contains("o route to host")) {
        err = "no route";
      } else if (timedOut) {
        err = "data timed out";
        SendEvent.edgeEvent("CWBWSTimeOut", tag + " data timed out!", this);
      }
      prta(tag + "  *** did not connect err=" + err);
      SendEvent.edgeSMEEvent("CWBWSNotUp", tag + " did not connect " + err, this);
    } else {
      prta(tag + "  *** e=" + e);
      SendEvent.edgeSMEEvent("CWBWSError", tag + " e=" + e, this);
    }

  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    if (args.length == 0) {
      args = new String[1];
      args[0] = "localhost:2060:2061:USDUG--BHZ00:USISCO-BHZ00:IUANMO-BHZ00:USCBSK-BHZ00";
    }
    QueryMomMonitor mon = new QueryMomMonitor(args[0], null);
    mon.setDebug(true);
    for (;;) {
      Util.sleep(1000);
    }
  }
}
