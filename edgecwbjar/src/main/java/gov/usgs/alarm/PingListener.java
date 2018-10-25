/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.net.SNWSender;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.TreeMap;

/**
 * This class sends pings to the given IP and keeps a static TreeMap of all of
 * these listeners.
 *
 * @author davidketchum
 */
public final class PingListener extends Thread {

  private static final TreeMap<String, PingListener> pings = new TreeMap();
  private static SNWSender send;
  private static DBConnectionThread dbconnstatus;
  private static final Integer dbmutex = Util.nextMutex();
  private String ipadr;
  private boolean terminate;
  private int lastTime;
  private int lastSeq;
  private long lastRcv;
  private BufferedReader in;
  private Subprocess sp;
  private String tag;
  private String shortTag;          // Normally the "station
  private Sender sender;

  @Override
  public String toString() {
    String lastAst = " ";
    String agoAst = " ";
    if (lastTime > 5000) {
      lastAst = "*";
    }
    if ((System.currentTimeMillis() - lastRcv) > 120000) {
      agoAst = "*";
    }
    return (tag.substring(0, tag.indexOf("[")) + "          ").substring(0, 8) + Util.rightPad(ipadr, 15)
            + " ping Time=" + (((lastTime) / 1000.) + "     ").substring(0, 3) + " s" + lastAst
            + Util.leftPad((((System.currentTimeMillis() - lastRcv) / 1000L) + "       ").substring(0, 7).trim(), 7) + " s ago" + agoAst + lastAst;
  }

  public static String pingReport() {
    if (pings == null) {
      return "There are no pings running on this node!\n";
    }
    Object[] p = pings.values().toArray();
    StringBuilder sb = new StringBuilder(p.length * 50);
    for (Object p1 : p) {
      sb.append(((PingListener) p1).toString()).append("\n");
    }
    return sb.toString();
  }

  public int getTime() {
    return lastTime;
  }

  public int getLastSeq() {
    return lastSeq;
  }

  public long getLastRcvMillis() {
    return lastRcv;
  }

  public String getTag() {
    return tag;
  }

  public String getIpadr() {
    return ipadr;
  }

  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * Creates a new instance of PingListener
   *
   * @param ip The ip address to ping
   * @param tg The tag for this listener
   */
  public PingListener(String ip, String tg) {
    tag = tg;
    ipadr = ip;
    shortTag = tag;
    terminate = false;
    try {
      if (Util.getOS().contains("Linux")) {
        sp = new Subprocess("ping -n -i 60 " + ip);
      } else {
        sp = new Subprocess("ping -n -I 60 " + ip); // Unix -I MacOS -i
      }      //sp = new Subprocess("ping -n -i 60 "+ip);    // Unix -I MacOS -i
      in = new BufferedReader(new InputStreamReader(sp.getOutputStream()));
      if (!(ip.equals("192.21.10.254") || ip.equals("192.10.21.254"))) {
        sender = new Sender();
      }
      PingListener p = (PingListener) pings.get(tag);
      if (p != null) {
        p.shutdown();
      }
      lastRcv = System.currentTimeMillis() - 1000000L;
      dbconnstatus = DBConnectionThread.getThread("PingStatus");
      if (dbconnstatus == null) {
        try {
          dbconnstatus = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status",
                  true, false, "PingStatus", Util.getOutput());
          if (!dbconnstatus.waitForConnection()) {
            if (!dbconnstatus.waitForConnection()) {
              if (!dbconnstatus.waitForConnection()) {
                if (!dbconnstatus.waitForConnection()) {
                  if (!dbconnstatus.waitForConnection()) {
                    Util.prta("PingListener failed to connect to database! " + Util.getProperty("DBServer"));
                  }
                }
              }
            }
          }

        } catch (InstantiationException e) {
          Util.prta("Instatniation exception no possible");
          dbconnstatus = DBConnectionThread.getThread("PingStatus");
        }
      }
      start();
    } catch (IOException e) {
      Util.prta("could not start ping!" + e.getMessage());
      if (e.getMessage().contains("Too many open files")) {
        Util.exit(0);
      }
    }
  }

  public void shutdown() {
    Util.prta(tag + " PingListener: Shutdown() called - terminate and interrupt()");
    sp.terminate();
    terminate = true;
    this.interrupt();
  }

  @Override
  public void run() {
    pings.put(tag, this);
    String line = null;
    tag += "[" + this.getName().substring(7) + "]";
    Util.prt(tag + "PingListener: starting " + ipadr);
    boolean change;
    PreparedStatement insert = null;
    int npings = 0;
    while (!terminate) {
      try {
        line = in.readLine();
        if (terminate) {
          break;
        }
      } catch (IOException e) {
        Util.prt(tag + " PingListener: IOExcept reading from ping subprocess " + e.getMessage());
      }
      if (line == null) {
        if (terminate) {
          break;
        }
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      } else {
        if (terminate) {
          break;
        }
        //Util.prt("line="+line);
        String[] tokens = line.split("[ =]");
        //Util.prt("# tokens="+tokens.length);
        change = false;
        for (int i = 0; i < tokens.length; i++) {
          if (tokens[i].equals("icmp_seq")) {
            lastSeq = Integer.parseInt(tokens[i + 1].substring(0, Math.max(1, tokens[i + 1].length() - 1)));
            change = true;
            //Util.prt("seq="+tokens[i+1]+"| is "+lastSeq);
          }
          if (tokens[i].equals("time")) {
            lastTime = (int) Double.parseDouble(tokens[i + 1]);
            //Util.prt("time="+tokens[i+1]+"| is "+lastTime);
            lastRcv = System.currentTimeMillis();
            change = true;
          }
        }
        if (change && (npings++ % 60) == 0 && !(ipadr.equals("192.10.21.254") || ipadr.equals("192.21.10.254"))) {
          int ok = 0;
          while (ok <= 1) {
            synchronized (dbmutex) {
              try {
                if (dbconnstatus == null) {
                  SendEvent.edgeSMEEvent("PingNoMySQL", "No mysql object in PingListener", this);
                  Util.exit(0);
                }
                //if(shortTag.indexOf("ISCO") >= 0) Util.prta("write "+shortTag+" npings="+npings+" lastTime="+lastTime+" seq="+lastSeq+" line="+line);
                if (insert == null) {
                  insert = dbconnstatus.prepareStatement(
                          " INSERT INTO status.ping (station,pingtime,pingseq) "
                          + " VALUES (?,?,?)", true);
                }
                insert.setString(1, shortTag);
                insert.setShort(2, (short) Math.min(32767, lastTime));
                insert.setShort(3, (short) (lastSeq % 32768));
                insert.executeUpdate();
                break;
                // dbconnstatus.executeUpdate(
                //      "INSERT INTO status.ping (station,pingtime,pingseq) "+
                //      " VALUES ('"+shortTag+"','"+Math.min(32767,lastTime)+"','"+(lastSeq%32768)+"')");
              } catch (SQLException e) {
                if (ok > 0) {
                  Util.SQLErrorPrint(e, tag + " PingListener: SQLException in inserting to ping table=" + e.getMessage() + " mysql=" + dbconnstatus.toString());
                }
                dbconnstatus.reopen();
                insert = null;
                ok++;
              }
            }
          }
        }
      }
    }
    sender.terminate();
    Util.prt(tag + " PingListener has terminated=" + terminate);
  }

  private final class Sender extends Thread {

    boolean terminate;
    StringBuilder sb = new StringBuilder(100);
    int hdrlen;

    public void terminate() {
      terminate = false;
      interrupt();
    }

    public Sender() {
      try {
        if (send == null) {
          send = new SNWSender(100, 50);
        }
      } catch (UnknownHostException e) {
        Util.prt(tag + " PingListener: bad host?");
      }
      if (tag.contains("[")) {
        Util.clear(sb).append(tag.substring(0, tag.indexOf("["))).append(":2:pingTime=");
      } else {
        Util.clear(sb).append(shortTag).append(":2:pingTime=");
      }
      hdrlen = sb.length();
      Util.prt("PingListenerSender : tag=" + tag + " sb=" + sb + " hdrlen=" + hdrlen);
      start();
    }

    @Override
    public void run() {
      while (!terminate) {
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
        sb.delete(hdrlen, sb.length());
        sb.append(lastTime / 1000.).append(";pingAge=").
                append((System.currentTimeMillis() - lastRcv) / 1000.).append('\n');
        send.queue(sb);
        //send.queue(tag.substring(0,tag.indexOf("["))+":2:pingTime="+Util.leftPad(""+lastTime/1000.,5)+
        //  ";pingAge="+(((System.currentTimeMillis() - lastRcv)/1000.)+"    ").substring(0,4).trim());
      }
      Util.prt(tag + "PingListener:Sender terminated");
    }
  }

  public static void main(String[] args) {
    Util.init();
    Util.setNoInteractive(true);
    Util.setModeGMT();
    PingListener pg = new PingListener("69.19.65.251", "TEST");
  }
}
