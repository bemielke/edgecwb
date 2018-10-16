/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.io.*;
import java.net.*;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.net.TimeoutSocketThread;
import gov.usgs.anss.util.PNZ;

/**
 * This class uses the Stasrv service (originally on neisa, nsn8) to retrieve metadata from the
 * server. It opens the server only once and keeps the link open so it can be used many times
 *
 * The service has a Timeout thread which can be set to interrupt a transaction by closing the
 * socket at a user specified number of millis (default=30);
 *
 * This is a descendant of the original StaSrv.java, but enhanced to throw IOExceptions and to be
 * using logging via an EdgeThread, and to allow alarms to be sent
 *
 * @author davidketchum
 */
public final class StaSrvEdge {

  private final String host;
  private final int port;
  private Socket s;         // socket to server
  private final byte[] b;        // buf space for I/O
  private String lastText;
  private boolean timedout;
  private final TimeoutSocketThread timeout;
  private final EdgeThread par;

  public void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * return string return by last query using this object
   *
   * @return the text from the last query
   */
  public String getLastText() {
    return lastText;
  }

  /**
   * Creates a new instance of StaSrv
   *
   * @param h The host string of the server to use
   * @param p The port to use on that server
   * @param parent If not null, the parent edgethread fro logging.
   * @throws IOException if opening server is a problem
   */
  public StaSrvEdge(String h, int p, EdgeThread parent) throws IOException {
    par = parent;
    if (h == null) {
      h = Util.getProperty("metadataserver");
    }
    if (h == null) {
      h = "cwbpub.cr.usgs.gov";     // USGS public metadata server
    }
    if (h.equals("")) {
      h = "cwbpub.cr.usgs.gov";
    }
    host = h;
    if (p <= 0) {
      port = 2052;
    } else {
      port = p;
    }
    b = new byte[1000];
    timeout = new TimeoutSocketThread(h + "/" + p, null, 30000);
    timeout.enable(false);
    openSocket();
  }

  /**
   * Creates a new instance of StaSrv
   *
   * @param h The host string of the server to use
   * @param p The port to use on that server
   * @param ms timeout time in millis
   * @param parent If not null, the parent edgethread fro logging.
   * @throws IOException if opening server is a problem
   */
  public StaSrvEdge(String h, int p, int ms, EdgeThread parent) throws IOException {
    par = parent;
    if (h == null) {
      h = Util.getProperty("metadataserver");
    }
    if (h == null) {
      h = "cwbpub.cr.usgs.gov";     // USGS public metadata server
    }
    if (h.equals("")) {
      h = "cwbpub.cr.usgs.gov";
    }
    host = h;
    port = p;
    b = new byte[1000];
    timeout = new TimeoutSocketThread(h + "/" + p, null, ms);
    timeout.enable(false);
    openSocket();
  }

  private void openSocket() throws IOException {
    prta("StaSrvEdge: Create new socket as it is null, closed of not connected! " + host + "/" + port);
    s = new Socket(host, port);
    timeout.setSocket(s);
  }
  /** close any open socket and reopen it
   * 
   * @throws IOException 
   */
  public void reopen() throws IOException {
    if(s != null) {
      if(!s.isClosed()) {
        try {
          s.close();
          
        }
        catch(IOException expected) {
        }
      }
    }
    openSocket();
  }
  /**
   * This method sends the line to the server and reads back the response. It reads until the two
   * linefeeds are found in a row indicating the end of the response. The read back data is
   * converted to a string and passed back to the caller for interpretation.
   *
   * @param line The command line to send formatted for the server
   * @return The data returned. If it is "", then socket is likely closed or server down.
   */
  private String send(String line) throws IOException {
    // Try the request twice before giving up.
    if (timedout) {
      return "";           // If this has timed out, never try again!
    }
    for (int loop = 0; loop < 15; loop++) {
      try {
        if (s == null || s.isClosed() || !s.isConnected()) {
          openSocket();
        }
        timeout.enable(true);
        s.getOutputStream().write(line.getBytes());
        int off = 0;
        int l;
        try {
          while ((l = s.getInputStream().read(b, off, 1000 - off)) >= 0) {
            off += l;
            //prta("read="+l+" off="+off);
            if (b[off - 1] == 10 && b[off - 2] == 10) {
              break;
            }
          }
          //s.close();
          //s=null;
          lastText = new String(b, 0, off);
          timeout.enable(false);
          return lastText;
        } catch (IOException e) {
          prta("getCoord: Error reading response" + e.getMessage());
          if (s != null) {
            try {
              s.close();
            } catch (IOException e2) {
            }
          }
          s = null;
          timeout.enable(false);
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e2) {
          }
          timeout.enable(false);
          if (loop > 2) {
            throw e;
          }
        }
      } catch (IOException e) {
        prta("Stasrv: IOException setting up socket to " + host + "/" + port + " loop=" + loop + " " + e.getMessage());
        if (s != null) {
          try {
            s.close();
          } catch (IOException e2) {
          }
        }
        s = null;
        timeout.enable(false);
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e2) {
        }
        if (e.toString().contains("Connection timed out")) {
          timedout = true;
          throw e;
        }// Marked timed out
        if (loop > 2) {
          throw e;
        }
      }
    }
    return "";
  }

  /**
   * This method sends the line to the server and reads back the response. It reads until EOR are
   * found indicating the end of the response. The read back data is converted to a string and
   * passed back to the caller for interpretation. This will try the request 3 times before giving
   * up.
   *
   * @param line The command line to send formatted for the server
   * @return The data returned. If it is "", then socket is likely closed or server down.
   */
  private String newSend(String line) throws IOException {
    StringBuilder sb = new StringBuilder(1000);
    int nlen = newSend(line, sb);

    return sb.toString();

  }

  /**
   * This method sends the line to the server and reads back the response. It reads until EOR are
   * found indicating the end of the response. The read back data is converted to a string and
   * passed back to the caller for interpretation. This will try the request 3 times before giving
   * up.
   *
   * @param line The command line to send formatted for the server
   * @param sb A stringbuilder where the response to this line will be returned (user must clear it)
   * @return The amount of data returned
   */
  private int newSend(String line, StringBuilder sb) throws IOException {
    timeout.enable(false);
    for (int ntry = 0; ntry < 3; ntry++) {

      try {
        if (s == null || s.isClosed() || !s.isConnected() || s.getInputStream() == null) {
          prta("StaSrv: Open new socket " + host + "." + port);
          s = new Socket(host, port);
          timeout.setSocket(s);
        }
      } catch (IOException e) {
        prta("StaSrv : error opening socket e=" + e);
        timeout.enable(false);
        throw e;
      }
      try {
        timeout.enable(true);
        s.getOutputStream().write(line.getBytes());
        int off = 0;
        int l = 0;
        while ((l = s.getInputStream().read(b, 0, 1000)) >= 0) {
          //prta("read="+l+" off="+off);
          for (int i = 0; i < l; i++) {
            sb.append((char) b[i]);    // Add these bytes to the string builder
          }          //String tmp = new String(b,0,l);
          //sb.append(tmp);
          off += l;
          // If the data ends with <EOR>\n then the request is done
          int k = sb.length() - 6;
          if (sb.charAt(k) == '<' && sb.charAt(k + 1) == 'E' && sb.charAt(k + 2) == 'O'
                  && sb.charAt(k + 3) == 'R' && sb.charAt(k + 4) == '>' && sb.charAt(k + 5) == '\n') {
            timeout.enable(false);
            return sb.length();
          }
        }
        prta("StaSrvEdge: newSend this should not happen len=" + sb.length() + " off=" + off + " l=" + l + "\n" + sb.toString());

      } catch (IOException e) {
        prta("StasrvEdge: IOError reading data from server " + host + "/" + port + " " + e.getMessage());
        if (ntry == 2) {
          throw e;
        }
      } catch (RuntimeException e) {
        prta("StaSrvEdge; Runtime err e==" + e);
        if (ntry == 2) {
          throw e;
        }
      }
      if (s != null) {
        try {
          s.close();
        } catch (IOException e2) {
        }
      }
      s = null;
    }
    timeout.enable(false);
    sb.append("* <EOR>\n");
    return sb.length();
  }

  /**
   * close the connection to the server /** getCoord returns an array with the lat, long, and elev
   *
   * @param net The network code
   * @param stat The station code
   * @param loc The location code
   * @return Array with lat, long and elev in that order. Zeros if station is unknown.
   * @throws IOException if opening server is a problem
   */
  public double[] getCoord(String net, String stat, String loc) throws IOException {
    String s2 = "COORD " + net.trim() + " " + stat.trim() + " " + loc.trim() + "\n";
    String resp = send(s2);
    double[] noans = new double[3];
    for (int i = 0; i < 3; i++) {
      noans[0] = 0.;
    }
    if (resp.contains("Stasrv: unknown") || resp.contains("invalid") || resp.contains("Runtime Exce")) {
      return noans;
    }
    String[] ans2 = resp.split(":");

    if (ans2.length < 2) {
      return noans;
    }
    ans2[1] = ans2[1].trim();
    ans2[1] = ans2[1].replaceAll("-", " -");
    ans2[1] = ans2[1].trim();
    for (int i = 0; i < 5; i++) {
      ans2[1] = ans2[1].replaceAll("  ", " ");
    }
    String[] ans = ans2[1].split("\\s");
    double[] ret = new double[3];

    try {
      for (int i = 0; i < Math.min(3, ans.length); i++) {
        ret[i] = Double.parseDouble(ans[i]);
      }
    } catch (NumberFormatException e) {
      prt("StaSsrvEdge: number format error on resp=" + resp
              + " ans=" + ans2[1] + "|" + (ans.length >= 1 ? ans[0] : "") + "|" + (ans.length >= 2 ? ans[1] : "") + "|"
              + (ans.length >= 3 ? ans[2] : ""));
      throw e;
    }
    return ret;
  }

  /**
   * get station comment stuff, location long name and network
   *
   * @param net The network code
   * @param stat The station code
   * @param loc The location code
   * @return Array with long name in first and network in second element.
   * @throws IOException if opening server is a problem
   */
  public String[] getComment(String net, String stat, String loc) throws IOException {
    String s2 = "CMNT " + net.trim() + " " + stat.trim() + " " + loc.trim() + "\n";
    String resp = send(s2);
    if (resp.contains("Stasrv: unknown")) {
      String[] ans2 = resp.split("\n");
      String[] ans = new String[2];
      ans[0] = ans2[0];
      ans[1] = "Unknown";
      return ans;
    }
    String[] ans2 = resp.split("\n");
    if (ans2.length == 1) {
      resp = ans2[0];
      ans2 = new String[2];
      ans2[0] = resp;
      ans2[1] = "Unknown";
    }
    ans2[0] = ans2[0].trim();
    ans2[1] = ans2[1].trim();
    return ans2;
  }

  /**
   * Returns net sta loc chan translated into Hydra notation (if net = IR) or into Chicxulub
   * notation otherwise. If no translation is found, the input is echoed (1 line, 15 characters).
   *
   * @param net The network code
   * @param stat The station code
   * @param loc The location code
   * @param chan The chanel code
   * @return Array with long name in first and network in second element. Stasrv: Unknown" on error.
   * @throws IOException if opening server is a problem
   */
  public String getTranslation(String net, String stat, String loc, String chan) throws IOException {
    String s2 = "TRAN " + net.trim() + " " + stat.trim() + " " + loc.trim() + " " + chan.trim() + "\n";
    String resp = send(s2);
    String[] ans2 = resp.split("\n");
    ans2[0] = ans2[0].trim();
    return ans2[0];
  }

  /**
   * get the response using the meta data server syntax, if the regular expression is not unique,
   * more than one response might be returned.
   *
   * @param channel regular expression to match
   * @param date Date for the epoch (yyyy/mm/dd-hh:mm)
   * @param unit The unit of the response (um or nm)
   * @return the SAC response string per the MDS
   * @throws IOException if opening server is a problem
   */
  public String getSACResponse(String channel, String date, String unit) throws IOException {
    String args = "-c r " + (date != null ? " -b " + date : "") + (unit != null ? " -u " + unit.trim() : "") + " -s " + channel.replaceAll(" ", "_") + "\n";
    String ans = newSend(args);
    return ans;
  }

  /**
   * get the response using the meta data server syntax, if the regular expression is not unique,
   * more than one response might be returned.
   *
   * @param channel regular expression to match
   * @param date Date for the epoch (yyyy/mm/dd-hh:mm)
   * @param unit The unit of the response (um or nm)
   * @param sb A user supplied StringBUilder (user must clear or it will be added to)
   * @return Number of characters in returned stringbuilder
   * @throws IOException if opening server is a problem
   */
  public int getSACResponse(String channel, String date, String unit, StringBuilder sb) throws IOException {
    String args = "-c r" + (date != null ? " -b " + date : "") + (unit != null ? " -u " + unit.trim() : "") + " -s " + channel.replaceAll(" ", "_") + "\n";
    return newSend(args, sb);
  }

  /**
   * return the comment 1=station, 2= network,
   *
   * @param channel A channel regular expression
   * @return The array with long name in [0], network in [1] or [0] will contain *** MDS: unknown
   * station"
   * @throws IOException if opening server is a problem
   */
  public String[] getMetaComment(String channel) throws IOException {
    String args = "-c d -s " + channel.replaceAll(" ", "_") + "\n";
    String[] ans = newSend(args).split("\n");

    return ans;
  }

  public String getDelazc(double min, double max, double lat, double lng) throws IOException {
    String args = "-delazc " + (min > 0. ? Util.df24(min) + ":" : "") + Util.df24(max) + ":" + lat + ":" + lng + " -c r\n";
    String resp = newSend(args);
    return resp;
  }
  /**
   * return the coordinates using the metadata server method rather than old stasrv
   *
   * @param channel Channel regular expression
   * @return Array with three values, if the station/channel is unknown all will be zero
   * @throws IOException if opening server is a problem
   */
  public double[] getMetaCoord(String channel) throws IOException {
    String args = "-c o -s " + channel.replaceAll(" ", "_") + "\n";
    String resp = newSend(args);
    double[] noans = new double[3];
    for (int i = 0; i < 3; i++) {
      noans[i] = 0;
    }
    if (resp.contains("no channels match")) {
      return noans;
    }
    String[] ans2 = resp.split(":");
    if (ans2.length < 3) {
      return noans;
    }
    ans2[2] = ans2[2].trim();
    ans2[2] = ans2[2].replaceAll("-", " -");
    ans2[2] = ans2[2].trim();
    for (int i = 0; i < 5; i++) {
      ans2[2] = ans2[2].replaceAll("  ", " ");
    }
    String[] ans = ans2[2].split("\\s");
    double[] ret = new double[3];
    try {
      for (int i = 0; i < Math.min(3, ans.length); i++) {
        ret[i] = Double.parseDouble(ans[i]);
      }
    } catch (NumberFormatException e) {
      Util.prt("StaSrv: number format error on resp=" + resp
              + " ans=" + ans2[1] + "|" + (ans.length >= 1 ? ans[0] : "") + "|" + (ans.length >= 2 ? ans[1] : "") + "|"
              + (ans.length >= 3 ? ans[2] : ""));
      throw e;
    }
    return ret;
  }

  /**
   * Returns net sta loc chan translated into Hydra notation (if net = IR) or into Chicxulub
   * notation otherwise. If no translation is found, the input is echoed (1 line, 15 characters).
   *
   * @param net The network code
   * @param stat The station code
   * @param loc The location code
   * @param chan The chanel code
   * @return a PNZ with the response. If error, the PNZ might be incomplete
   * @throws IOException if error reading data or opening server
   */
  public PNZ getCookedResponse(String net, String stat, String loc, String chan) throws IOException {
    String s2 = "RESP " + net.trim() + " " + stat.trim() + " " + loc.trim() + " " + chan.trim() + "\n";
    String resp = send(s2);
    if (resp.contains("Stasrv: unknown")) {
      return null;
    }
    return new PNZ(resp);
  }

  /**
   * get Orientation of the channel
   *
   * @param net network code
   * @param stat station code
   * @param loc location code
   * @param chan channel code
   * @return 3 element array 0=azimuth in degrees clockwise from north, 1=dip in degrees from
   * horizontal, 2=depth in meters from surface.
   * @throws IOException if opening server is a problem
   */
  public double[] getOrientation(String net, String stat, String loc, String chan) throws IOException {
    String s2 = "ORIENT " + net.trim() + " " + stat.trim() + " " + loc.trim() + " " + chan.trim() + "\n";
    String resp = send(s2);
    //Util.prt(resp);
    if (resp.contains("Stasrv: unknown")) {
      return null;
    }
    String[] ans2 = resp.split(":");
    if (ans2.length < 2) {
      Util.prt("   *** ORIENT did not get properly formatted output for " + s2 + "=" + resp);
      return null;
    }
    ans2[1] = ans2[1].trim();
    ans2[1] = ans2[1].replaceAll("-", " -");
    ans2[1] = ans2[1].trim();
    for (int i = 0; i < 10; i++) {
      ans2[1] = ans2[1].replaceAll("  ", " ");
    }
    String[] ans = ans2[1].split("\\s");
    double[] ret = new double[3];
    if (ans.length >= 4) {
      if (!ans[ans.length - 1].trim().equals("*")) {
        return null;
      }
    }
    try {
      for (int i = 0; i < Math.min(3, ans.length); i++) {
        ret[i] = Double.parseDouble(ans[i]);
      }
    } catch (NumberFormatException e) {
      Util.prt("stasrv number format error on resp=" + resp
              + " ans=" + ans2[1] + "|" + (ans.length >= 1 ? ans[0] : "") + "|" + (ans.length >= 2 ? ans[1] : "") + "|"
              + (ans.length >= 3 ? ans[2] : ""));
      throw e;
    }
    return ret;
  }

  /**
   * test main
   *
   * @param args - not used
   */
  public static void main(String[] args) {
    try {
      StaSrvEdge srv = new StaSrvEdge("cwbpub.cr.usgs.gov", 2052, null);
      String s = srv.getSACResponse("USDUG  ...", "2007,318-00:00:00", "nm");
      double[] coords = srv.getMetaCoord("USDUG  BHZ00");
      String s2 = srv.newSend("-s USDUG__.H... -c o -b 2008/01/01-00:00:00\n");
      Util.prt(s2);
      String polesAndZeros = srv.getSACResponse("USDUG  ...", "2013/12/31-12:00", "NM");
      String[] comment = srv.getMetaComment("USDUG  BHZ00");
      Util.prt("coord=" + coords[0] + " " + coords[1] + " " + coords[2] + " comnt=" + comment[0] + (comment.length > 1 ? " [1]=" + comment[1] : "")
              + " resp=" + polesAndZeros);
      double[] orient = srv.getOrientation("IM", "MK09", "  ", "SHZ");
      if (orient != null) {
        for (int i = 0; i < orient.length; i++) {
          Util.prta(" [" + i + "]=" + orient[i]);
        }
      }
      double[] coord = srv.getCoord("RO", "BUR01", "  ");
      PNZ pnz = srv.getCookedResponse("US", "BOZ", "  ", "BHZ");
      Util.prt("SAC format\n" + pnz.toSACString("nm"));
      Util.prta("lat=" + coord[0] + " long=" + coord[1] + " elev=" + coord[2]);
      Util.prta(pnz.toString() + " amp 1 hz=" + pnz.getAmp(1.) + " ph=" + pnz.getPhase(1.)
              + " .01=" + pnz.getAmp(0.01) + " ph=" + pnz.getPhase(0.01));
      orient = srv.getOrientation("US", "GOGA", "  ", "BHZ");
      if (orient != null) {
        for (int i = 0; i < orient.length; i++) {
          Util.prta(" [" + i + "]=" + orient[i]);
        }
      }
      orient = srv.getOrientation("IR", "ISCO", "  ", "BHNS");
      if (orient != null) {
        for (int i = 0; i < orient.length; i++) {
          Util.prta(" [" + i + "]=" + orient[i]);
        }
      }
      orient = srv.getOrientation("US", "ECSD", "HR", "BHE");
      if (orient != null) {
        for (int i = 0; i < orient.length; i++) {
          Util.prta(" [" + i + "]=" + orient[i]);
        }
      }
      orient = srv.getOrientation("US", "KSU1", "HR", "BHZ");
      if (orient != null) {
        for (int i = 0; i < orient.length; i++) {
          Util.prta(" [" + i + "]=" + orient[i]);
        }
      }
      orient = srv.getOrientation("US", "KSU1", "HR", "BH2");
      if (orient != null) {
        for (int i = 0; i < orient.length; i++) {
          Util.prta(" [" + i + "]=" + orient[i]);
        }
      }
      orient = srv.getOrientation("US", "KSU1", "HR", "BH1");
      if (orient != null) {
        for (int i = 0; i < orient.length; i++) {
          Util.prta(" [" + i + "]=" + orient[i]);
        }
      }
      pnz = srv.getCookedResponse("IR", "SDDR", "  ", "BHZ");
      Util.prta(pnz.toString() + " amp 1 hz=" + pnz.getAmp(1.) + " ph=" + pnz.getPhase(1.)
              + " .01=" + pnz.getAmp(0.01) + " ph=" + pnz.getPhase(0.01));
      Util.prta("tran IRSDDR=" + srv.getTranslation("IR", "SDDR", "  ", "BHZ")
              + " Ill=" + srv.getTranslation("IR", "SDDR", "  ", "BHZ"));
      String[] ans = srv.getComment("IR", "GWDE", "  ");
      Util.prta("0=" + ans[0] + " 1=" + ans[1]);
      coord = srv.getCoord("US", "IIII", "  ");
      Util.prta("lat=" + coord[0] + " long=" + coord[1] + " elev=" + coord[2]);
      coord = srv.getCoord("IR", "GWDE", "  ");
      Util.prta("lat=" + coord[0] + " long=" + coord[1] + " elev=" + coord[2]);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
