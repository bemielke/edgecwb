/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.channelstatus;

import java.net.*;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import gov.usgs.anss.util.*;

/**
 * This class sets up a connection to a Latency server which runs in a UdpChannel and allows the
 * server to be queried for ChannelStatus via the getSeedname() method getStation() or getList() methods.
 * Useful in applications that need to track latency or last minutes received like some of the fetchers
 * to control input rates on fetches.0
 *
 * <pre>
 * Switch     Arg           Description
 * Setup 
 * -h        Host.ip      Where a LatencyServer (default= acqdb)
 * -p        port         Port of the latency Server (default=7956)
 * Commands     
 * -c        NNSSSSSCCCLL Give a single channel to probe using latency/lastrecv flags
 * -s        NNSSSSS      Get an station best estimate (uses a channel heuristic) using latency/lastrecv flags
 * -l                     Generate a list of all channels.  DUmp is of all ChannelStatus information
 
* Modifiers
 * -match    Regexp       If a -l, this limits the output to channels matching the regexp.
 * -latency               If a -c or -s command is used, the latency is output
 * -lastrecv              If a -c or -s command is used, the last-recv is output
 *                        If neither modifier is present on a -c or -s the full ChannelStatus is printed
 * </PRE>
 * @author davidketchum
 */
public class LatencyClient {

  private final String host;
  private final int port;
  private ChannelStatus cs;
  private final int maxlength;
  private final byte[] b;

  public LatencyClient(String h, int p) {
    host = h;
    port = p;
    maxlength = ChannelStatus.getMaxLength();
    b = new byte[maxlength];
  }

  public synchronized ChannelStatus getSeedname(String seedname) throws IOException, UnknownHostException {
    Socket s = null;
    try {
      s = new Socket(host, port);
      s.getOutputStream().write((seedname + "        ").substring(0, 12).getBytes());
      int len = s.getInputStream().read(b);
      if (len < maxlength) {
        if (len == -1) {
          Util.prt("LatC: could not find seedname=" + seedname);
        } else {
          Util.prt("LatC: Did not read full length =" + len + "!=" + maxlength + " for seedname=" + seedname);
        }
        s.close();
        return null;
      }
      if (cs == null) {
        cs = new ChannelStatus(b);
      } else {
        cs.reload(b);
      }
      //Util.prta("Seedname="+seedname+" cs="+cs);
    } catch (UnknownHostException e) {
      throw e;
    } catch (IOException e) {
      throw e;
    }
    //if(s != null)
    if (!s.isClosed()) {
      try {
        s.close();
      } catch (IOException expected) {
      }
    }
    return cs;
  }

  public synchronized ChannelStatus getStation(String seedname) throws IOException, UnknownHostException {
    Socket s = null;
    try {
      s = new Socket(host, port);
      s.getOutputStream().write((seedname + "        ").substring(0, 7).getBytes());
      int len = s.getInputStream().read(b);
      if (len < maxlength) {
        if (len == -1) {
          Util.prt("LatC: Could not find station=" + seedname);
        } else {
          Util.prt("LatC: Did not read full length =" + len + "!=" + maxlength + " for station=" + seedname);
        }
        s.close();
        return null;
      }
      if (cs == null) {
        cs = new ChannelStatus(b);
      } else {
        cs.reload(b);
      }
      //Util.prta("Seedname="+seedname+" cs="+cs);
    } catch (UnknownHostException e) {
      throw e;
    } catch (IOException e) {
      throw e;
    }
    //if(s != null)
    if (!s.isClosed()) {
      try {
        s.close();
      } catch (IOException expected) {
      }
    }
    return cs;
  }

  public synchronized StringBuilder getList() throws IOException, UnknownHostException {
    Socket s = null;
    StringBuilder sb = new StringBuilder(10000);
    byte[] b2 = new byte[2500];
    int len;
    try {
      s = new Socket(host, port);
      s.getOutputStream().write("*".getBytes());
      while ((len = s.getInputStream().read(b2)) > 0) {
        sb.append(new String(b2, 0, len));
        //Util.prta("Seedname="+seedname+" cs="+cs);
      }
    } catch (UnknownHostException e) {
      throw e;
    } catch (IOException e) {
      throw e;
    }
    //if(s != null)
    if (!s.isClosed()) {
      try {
        s.close();
      } catch (IOException expected) {
      }
    }
    return sb;
  }

  public static void main(String[] args) {
    String host = "acqdb";
    Util.setModeGMT();
    Util.init("edge.prop");
    int port = 7956;
    String seedname = "IUANMO-";
    boolean list = false;
    boolean latency = false;
    boolean lastrecv = false;
    String re="";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
      }
      if (args[i].equals("-c")) {
        seedname = (args[i + 1] + "        ").substring(0, 12).replaceAll("-", " ").replaceAll("_", " ");
        i++;
      }
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      }
      if (args[i].equals("-s")) {
        seedname = (args[i + 1] + "       ").substring(0, 7).replaceAll("-", " ").replaceAll("_", " ");
        i++;
      }
      if (args[i].equals("-l")) {
        list = true;
        seedname = "*";
      }
      if (args[i].equals("-latency")) {
        latency = true;
      }
      if (args[i].equals("-lastrecv")) {
        lastrecv = true;
      }
      if(args[i].equals("-match")) {
        re = args[i+1];
      }

    }
    LatencyClient c = new LatencyClient(host, port);
    try {
      ChannelStatus cs = null;
      if (list) {
        StringBuilder sb = c.getList();
        if(re.equals("")) {
          Util.prt(sb);
        }
        else {
          try {
            BufferedReader in = new BufferedReader(new StringReader(sb.toString()));
            String line;
            while( (line = in.readLine() ) != null) {
              int start = line.indexOf("k=")+2;
              
              String chan =  line.substring(start, start+12);
              if(chan.matches(re)) {
                Util.prt(line);
              }
            }
          }
          catch(IOException e) {
            e.printStackTrace();
          }
        }
      } else if (seedname.length() == 12) {
        cs = c.getSeedname(seedname);
      } else if (seedname.length() == 7) {
        cs = c.getStation(seedname.substring(0, 7));
        //Util.prt("" + cs);
      } else {
        StringBuilder sb = c.getList();
        Util.prt(sb.toString());
      }
      StringBuilder out = new StringBuilder(100);
      if (cs != null) {
        if (latency) {
          out.append(cs.getLatency()).append(" ");
        }
        if (lastrecv) {
          out.append(Util.df21(cs.getAge()));
        }
        if (out.length() == 0) {
          out.append(cs.toString()).append(" lrcv-m=").append(Util.df21(cs.getAge()));
        }
        Util.prt(out);
      } else {
        Util.prta(seedname + " did not return a latency.");
      }
    } catch (UnknownHostException e) {
      Util.prt("Unknown host=" + e);
    } catch (IOException e) {
      Util.prt("IOError getting latency=" + e);
    }
    System.exit(0);
  }
}
