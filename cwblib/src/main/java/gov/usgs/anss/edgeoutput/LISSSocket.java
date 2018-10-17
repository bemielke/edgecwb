/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import java.net.Socket;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edge.IllegalSeednameException;

/**
 *
 * @author davidketchum
 */
public final class LISSSocket extends Thread {

  private boolean running;
  private boolean dbg;
  private final Socket s;
  private final LISSStationServer parent;
  private final EdgeThread par;
  private final String tag;
  private long lastWrite;
  private final long connected;
  private long latencyAvg;
  private long nlatency;
  private long minLatency;
  private long maxLatency;
  private int nextout;           // Next place to put data
  private int npacket;
  private boolean terminate;
  private PrintStream loglat;

  public int getNPackets() {
    return npacket;
  }

  /**
   * terminate this thread
   */
  public void terminate() {
    terminate = true;
    interrupt();
    try {
      if (!s.isClosed()) {
        s.close();
      }
    } catch (IOException e) {
    }
  }

  /**
   * if true, this thread is running
   *
   * @return true if running
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * get the tag associated with this thread
   *
   * @return the tag
   */
  public String getTag() {
    return tag;
  }

  /**
   * return a millisecond time of the last output (used to time out connections
   *
   * @return The millisecond of the last output
   */
  public long getLastWrite() {
    return lastWrite;
  }

  public String getStatistics() {
    String s2 = "Latency min=" + minLatency + " max=" + maxLatency + " #pck=" + nlatency + " avg=" + (nlatency > 0 ? (latencyAvg / nlatency) : 0);
    nlatency = 0;
    latencyAvg = 0;
    minLatency = 100000000;
    maxLatency = -100000000;
    return s2;
  }

  /**
   * return a string summarizing this thread
   *
   * @return The string
   */
  @Override
  public String toString() {
    return tag + " #pkt=" + npacket + " connect=" + (System.currentTimeMillis() - connected) / 1000 + " s";
  }

  /**
   * Creates a new instance of LISSSocket
   *
   * @param sock A socket this thread will use for communication (a LISSS client
   * @param srv The LISSStationServer which will contain data for this station
   * @param next The nextout value to ring queue (last packet that was put in)
   * @param tg The tag to hang on this thread
   * @param pr The parent thread to use for logging
   */
  public LISSSocket(Socket sock, LISSStationServer srv, int next, String tg, EdgeThread pr) {
    s = sock;
    tag = tg + "[" + s.getInetAddress().toString().substring(1) + "/" + s.getPort() + "]";
    nextout = next;
    parent = srv;
    par = pr;
    connected = System.currentTimeMillis();
    running = true;
    lastWrite = System.currentTimeMillis();
    if (srv.getStation().trim().equals(ChannelHolder.getDebugSeedname().trim())) {
      dbg = true;
    }
    par.prta(tag + "LISSSocket srv=" + srv + " " + srv.getStation() + "|" + ChannelHolder.getDebugSeedname() + "| dbg=" + dbg);
    start();
  }

  @Override
  public void run() {
    running = true;
    byte[] buf = new byte[512];
    int iday = -1;
    try {
      OutputStream out = s.getOutputStream();
      boolean newData;
      for (;;) {
        if (terminate) {
          break;
        }
        try {
          newData = parent.dequeue(nextout, buf);
          if (newData) {
            out.write(buf);         // write data to receiver

            // The following measure latency of sends and write them in latency files.
            int[] t = MiniSeed.crackTime(buf);
            long ms = t[0] * 3600000L + t[1] * 60000L + t[2] * 1000L + t[3] / 10 + ((long) (MiniSeed.crackNsamp(buf) * 1000. / MiniSeed.crackRate(buf)));
            ms = (System.currentTimeMillis() % 86400000L) - ms;
            if (ms < 0) {
              ms += 86400000;
            }
            //if(MiniSeed.crackSeedname(buf).substring(7,10).equals("LOG")) par.prta("LOG recout to socket="+MiniSeed.crackSeedname(buf));
            if (MiniSeed.crackSeedname(buf).substring(7, 10).equals("BHZ")) {
              if (ms < minLatency) {
                minLatency = ms;
              }
              if (ms > maxLatency) {
                maxLatency = ms;
              }
              latencyAvg += ms;
              nlatency++;
            }
            if (dbg) {
              par.prta(tag + " pkt out " + MiniSeed.crackSeedname(buf) + " " + MiniSeed.crackNsamp(buf) + " " + MiniSeed.crackRate(buf)
                      + " " + t[0] + ":" + t[1] + ":" + t[2] + "." + t[3] + " lat=" + ms);
            }
            // record latencies for each station with a connection
            /*if(System.currentTimeMillis() / 86400000L != iday) {
              if(loglat != null) loglat.close();
              iday =(int) (System.currentTimeMillis() / 86400000L);
              try {
                  loglat = new PrintStream(
                      new FileOutputStream(
                        Util.getProperty("logfilepath")+parent.getStation().trim()+"_"+
                      s.getInetAddress().toString().substring(10).replaceAll("/","").trim()+".lat"+EdgeThread.EdgeThreadDigit(), true));
              }
              catch(FileNotFoundException e) {}
            }
            if(out != null && buf[17] == 'Z') loglat.println(
                Util.leftPad(""+(System.currentTimeMillis()%86400000L),9)+Util.leftPad(""+ms,10)+" "+
                MiniSeed.crackSeedname(buf));*/

            // record last time written and update our pointer to ring
            lastWrite = System.currentTimeMillis();
            nextout = parent.next(nextout);
            npacket++;
            if (npacket % 1000 == 0) {
              par.prta(toString());
            }
          } else {
            if (System.currentTimeMillis() - lastWrite > 240000) {
              par.prta(toString() + " Send heartbeat");
              for (int i = 0; i < 6; i++) {
                buf[i] = '0';
              }
              for (int i = 6; i < 512; i++) {
                buf[i] = ' ';
              }
              out.write(buf);
              lastWrite = System.currentTimeMillis();
            }
            try {
              sleep(100);
            } catch (InterruptedException e) {
            }
          }
        } catch (IOException e) {
          Util.SocketIOErrorPrint(e, tag + " LISSSocket to " + s, par.getPrintStream());
          break;
        } catch (IllegalSeednameException e) {
          par.prta("Not a mini seed in LISSSOCket=" + MiniSeed.toStringRaw(buf));
        }
      }
      par.prta(tag + " is terminated.");
    } catch (IOException e) {
      par.prta(tag + "IOException getting output unit to socket.  Die.");
    }
    try {
      if (!s.isClosed()) {
        s.close();
      }

    } catch (IOException e) {
    }
    running = false;
  }
}
