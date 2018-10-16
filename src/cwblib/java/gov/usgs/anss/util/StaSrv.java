/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.IOException;
import java.net.Socket;
import gov.usgs.anss.net.TimeoutSocketThread;

/**
 * This class uses the Stasrv service (originally on neisa, nsn8) to retrieve metadata from the
 * server. It opens the server only once and keeps the link open so it can be used many times.
 * <br>
 *
 * The service has a Timeout thread which can be set to interrupt a transaction by closing the
 * socket at a user specified number of millis (default=30);
 *
 *
 * @author davidketchum
 */
public final class StaSrv {

  private String host;
  private int port;
  private Socket s;         // socket to server
  private final byte[] b;        // buf space for I/O
  private String lastText;
  private boolean timedout;
  private final TimeoutSocketThread timeout;

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
   * @param h The host string of the server to use (try metadataserver property, then
   * def=cwbpub.cr.usgs.gov)
   * @param p The port to use on that server (if less than or equal to zero def=2052)
   */
  public StaSrv(String h, int p) {
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
    if (port <= 0) {
      port = 2052;
    }
    b = new byte[1000];
    timeout = new TimeoutSocketThread(h + "/" + p, null, 30000);
    timeout.enable(false);
  }

  /**
   * Creates a new instance of StaSrv
   *
   * @param h The host string of the server to use (if null or empty property metdataserver
   * def=cwbpub.cr.usgs.gov)
   * @param p The port to use on that server (if less or equal to zero def=2052)
   * @param ms timeout time in millis
   */
  public StaSrv(String h, int p, int ms) {
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
    if (port <= 0) {
      port = 2052;
    }
    b = new byte[1000];
    timeout = new TimeoutSocketThread(h + "/" + p, null, ms);
    timeout.enable(false);
  }

  /**
   * This method sends the line to the server and reads back the response. It reads until the two
   * linefeeds are found in a row indicating the end of the response. The read back data is
   * converted to a string and passed back to the caller for interpretation.
   *
   * @param line The command line to send formatted for the server
   * @return The data returned. If it is "", then socket is likely closed or server down.
   */
  private String send(String line) {
    // Try the request twice before giving up.
    if (timedout) {
      return "";           // If this has timed out, never try again!
    }
    for (int loop = 0; loop < 15; loop++) {
      try {
        if (s == null || s.isClosed() || !s.isConnected()) {
          System.out.println("StaSrv: Create new socket as it is null, closed of not connected! " + host + "/" + port);
          s = new Socket(host, port);
          timeout.setSocket(s);
        }
        timeout.enable(true);
        s.getOutputStream().write(line.getBytes());
        int off = 0;
        int l;
        try {
          while ((l = s.getInputStream().read(b, off, 1000 - off)) >= 0) {
            off += l;
            //System.out.println("read="+l+" off="+off);
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
          System.out.println("getCoord: Error reading response" + e.getMessage());
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
        }
      } catch (IOException e) {
        System.out.println("Stasrv: IOException setting up socket to " + host + "/" + port + " loop=" + loop + " " + e.getMessage());
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
          timedout = true;
          break;
        }// Marked timed out
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
  private String newSend(String line) {
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
  private int newSend(String line, StringBuilder sb) {
    timeout.enable(false);
    for (int ntry = 0; ntry < 3; ntry++) {

      try {
        if (s == null || s.isClosed() || !s.isConnected() || s.getInputStream() == null) {
          Util.prta("StaSrv: Open new socket " + host + "." + port);
          s = new Socket(host, port);
          timeout.setSocket(s);
        }
        timeout.enable(true);
        s.getOutputStream().write(line.getBytes());
        int off = 0;
        int l = 0;
        try {
          while ((l = s.getInputStream().read(b, 0, 1000)) >= 0) {
            //System.out.println("read="+l+" off="+off);
            for (int i = 0; i < l; i++) {
              sb.append((char) b[i]);    // Add these bytes to the string builder
            }            //String tmp = new String(b,0,l);
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
          System.out.println("newSend this should not happen len=" + sb.length() + " off=" + off + " l=" + l + "\n" + sb.toString());
        } catch (IOException e) {
          System.out.println("getCoord: IOError reading response" + e.getMessage());
        }
      } catch (IOException e) {
        System.out.println("Stasrv: IOError setting up socket to " + host + "/" + port + " " + e.getMessage());
      } catch (RuntimeException e) {
        System.out.println("Runtime err e==" + e);
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
   * This allows select statements to be run against the database. Data is returned in a string
   * builder with the fields separated by pipe characters
   *
   * @param select The select statement to issue
   * @param sb User StringBuild which will have the results appended.
   */
  public void doSelect(String select, StringBuilder sb) {
    if (select.substring(0, 6).equalsIgnoreCase("SELECT")) {
      newSend(select + (select.endsWith("\n") ? "" : "\n"), sb);
    } else {
      sb.append("NOT a SELECT statement sql=").append(select).append("\n<EOR>\n");
    }
  }

  /**
   * close the connection to the server /** getCoord returns an array with the lat, long, and elev
   *
   * @param net The network code
   * @param stat The station code
   * @param loc The location code
   * @return Array with lat, long and elev in that order. Zeros if station is unknown.
   */
  public double[] getCoord(String net, String stat, String loc) {
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
      Util.prt("stasrv number format error on resp=" + resp
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
   * @return Array with long name in first and network in second element. "Stasrv: unknown" and
   * "Unknown"if error.
   */
  public String[] getComment(String net, String stat, String loc) {
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
   */
  public String getTranslation(String net, String stat, String loc, String chan) {
    String s2 = "TRAN " + net.trim() + " " + stat.trim() + " " + loc.trim() + " " + chan.trim() + "\n";
    String resp = send(s2);
    String[] ans2 = resp.split("\n");
    ans2[0] = ans2[0].trim();
    return ans2[0];
  }

  /**
   * get the response using the meta data server syntax, if the regular expression is not unique,
   * more than one response might be returned. This command uses MDS formats.
   *
   * @param channel regular expression to match
   * @param date Date for the epoch (yyyy/mm/dd-hh:mm)
   * @param unit The unit of the response (um or nm)
   * @return
   */
  public String getSACResponse(String channel, String date, String unit) {
    String args = "-c r" + (date != null ? " -b " + date : "") + " -u " + unit.trim() + " -s " + channel.replaceAll(" ", "_") + "\n";
    String ans = newSend(args);
    return ans;
  }

  /**
   * get the response using the meta data server syntax, if the regular expression is not unique,
   * more than one response might be returned. This command uses MDS formats.
   *
   * @param channel regular expression to match
   * @param date Date for the epoch (yyyy/mm/dd-hh:mm)
   * @param unit The unit of the response (um or nm)
   * @param sb A user supplied StringBUilder (user must clear or it will be added to)
   * @return
   */
  public int getSACResponse(String channel, String date, String unit, StringBuilder sb) {
    String args = "-c r" + (date != null ? " -b " + date : "") + (unit != null ? " -u " + unit.trim() : "") + " -s " + channel.replaceAll(" ", "_") + "\n";
    return newSend(args, sb);
  }

  /**
   * get the response using the meta data server syntax, if the regular expression is not unique,
   * more than one response might be returned. This command uses MDS formats.
   *
   * @param channel regular expression to match
   * @param date Date for the epoch (yyyy/mm/dd-hh:mm)
   * @param unit The unit of the response (um or nm)
   * @param sb A user supplied StringBUilder (user must clear or it will be added to)
   * @return
   */
  public int getSACResponse(StringBuilder channel, String date, String unit, StringBuilder sb) {
    String args = "-c r" + (date != null ? " -b " + date : "") + (unit != null ? " -u " + unit.trim() : "") + " -s " + Util.stringBuilderReplaceAll(channel, ' ', '_') + "\n";
    return newSend(args, sb);
  }

  /**
   * return the comment 1=station, 2= network,
   *
   * @param channel A channel regular expression
   * @return The array with long name in [0], network in [1] or [0] will contain *** MDS: unknown
   * station"
   */
  public String[] getMetaComment(String channel) {
    return getMetaComment(channel, null);
  }

  /**
   * return the comment 1=station, 2= network,
   *
   * @param channel A channel regular expression
   * @param date A MDS type date
   * @return The array with long name in [0], network in [1] or [0] will contain *** MDS: unknown
   * station"
   */
  public String[] getMetaComment(String channel, String date) {
    String args = "-c d -s " + channel.replaceAll(" ", "_") + (date != null ? " -b " + date : "") + "\n";
    String[] ans = newSend(args).split("\n");

    return ans;
  }

  /**
   * Get the site description only at the current date
   *
   * @param station the NNSSSSS to get
   * @return the site description or "unknown station in MDS"
   */
  public String getSiteDescription(String station) {
    return getSiteDescription(station, null);
  }

  /**
   * Get the site description only the given date. if date = all it will get the newest one
   *
   * @param station the NNSSSSS to get
   * @param date The date with all of the station epochs to get, if "all" then the latest one is
   * returned
   * @return the site description or "unknown station in MDS"
   */
  public String getSiteDescription(String station, String date) {
    String[] ans = getMetaStation(station, date);
    for (int i = ans.length - 1; i >= 0; i--) {
      if (ans[i].contains("SeedSiteName")) {
        return ans[i].substring(15).trim();
      }
    }
    return "unknown station in MDS " + station;
  }

  /**
   * return the station text (see getMetaStation(station,date) for more details
   *
   * @param station A station regexp
   * @return An array of strings with one string per line of the response station"
   */
  public String[] getMetaStation(String station) {
    return getMetaStation(station, null);
  }

  /**
   * return the station description (example below) as an array of strings
   * <pre>
   * * Station          3AL002
   * IRcode
   * OtherAlias
   * EpochBegin       2010-03-30 00:00:00.0
   * EpochEnd         2010-12-31 23:59:59.0
   * SeedSiteName     D404
   * SeedOwner        UK
   * IRName
   * IROwner
   * SeedURL          ./METADATA/DATALESS/3A.dataless
   * XMLURL           ./METADATA/XML/noxml.html
   * Latitude         -38.2551
   * Longitude        -72.257004
   * Elevation        410.0
   * SeedLatitude     -38.2551
   * SeedLongitude    -72.257004
   * SeedElevation    410.0
   * SEEDElevMethod    SEED Elevation Method Unknown
   * </PRE>
   *
   * @param station A channel regular expression
   * @param date A MDS type date
   * @return The array with long name in [0], network in [1] or [0] will contain *** MDS: unknown
   * station"
   */
  public String[] getMetaStation(String station, String date) {
    String args = "-c s -s " + station.replaceAll(" ", "_") + (date != null ? " -b " + date : "") + "\n";
    String[] ans = newSend(args).split("\n");

    return ans;
  }

  /**
   * return the coordinates using the metadata server method rather than old stasrv
   *
   * @param channel Channel regular expression
   * @return Array with three values, if the station/channel is unknown all will be zero
   */
  public double[] getMetaCoord(String channel) {
    return getMetaCoord(channel, null);
  }

  /**
   * return the coordinates using the metadata server method rather than old stasrv
   *
   * @param channel Channel regular expression
   * @param date A string suitable for MDS consumption
   * @return Array with three values, if the station/channel is unknown all will be zero
   */
  public double[] getMetaCoord(String channel, String date) {
    String args = "-c o -s " + channel.replaceAll(" ", "_") + (date != null ? " -b " + date : "") + "\n";
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

  public String getDelazc(double min, double max, double lat, double lng) {
    String args = "-delazc " + (min > 0. ? Util.df24(min) + ":" : "") + Util.df24(max) + ":" + lat + ":" + lng + " -c r\n";
    String resp = newSend(args);
    return resp;
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
   */
  public PNZ getCookedResponse(String net, String stat, String loc, String chan) {
    String s2 = "RESP " + net.trim() + " " + stat.trim() + " " + loc.trim() + " " + chan.trim() + "\n";
    String resp = send(s2);
    if (resp.contains("Stasrv: unknown")) {
      return null;
    }
    return new PNZ(resp);
  }
  public static final String mdgetHelp = "MDGET version 1.6 2015/05/21 def server=cwbpub.cr.usgs.gov port=2052\n"
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
          + "-p nnnn  Use port nnnn instead of the default server port number (2052)";

  public String mdget(String[] args) {
    StringBuilder sb = new StringBuilder(100);
    StringBuilder sql = null;
    boolean dbg = false;
    boolean kml = false;
    String dirstub = "./";
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
      if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
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
          Util.prt("-c arguments must be a, c, d, l, o, r, or s\n");
          System.exit(1);
        }
        i++;
      } else if (args[i].equals("-b") || args[i].equals("-e")
              || args[i].equals("-s")) {
        sb.append(args[i]).append(" ").append(args[i + 1].replaceAll(" ", "-")).append(" ");
        i++;
      } else if (args[i].equals("-help") || args[i].equals("-?")) {
        return mdgetHelp;
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
        if (args[i].equals("-a")) {
          sb.append("-c c ");
        }
        i++;
      } else {
        Util.prt("Bad argument at " + i + " args=" + args[i]);
        System.exit(3);
      }
    }
    /* if no -c has been selected by the arguments, default to get responses */
    if (sb.indexOf("-c") < 0) {
      sb.append("-c r ");
    }
    sb.append("\n");

    //for(int i=0; i<args.length; i++) sb.append(args[i]).append(i == args.length-1?"\n":" ");
    // Try the request twice before giving up.
    if (timedout) {
      return "";           // If this has timed out, never try again!
    }
    for (int loop = 0; loop < 15; loop++) {
      try {
        if (s == null || s.isClosed() || !s.isConnected()) {
          //.out.println("StaSrv: Create new socket as it is null, closed of not connected! "+host+"/"+port);
          s = new Socket(host, port);
          timeout.setSocket(s);
        }
        timeout.enable(true);
        Util.stringBuilderToBuf(sb, b);
        s.getOutputStream().write(b, 0, sb.length());
        Util.clear(sb);
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
          lastText = sb.toString();
          timeout.enable(false);
          return lastText;
        } catch (IOException e) {
          System.out.println("getCoord: Error reading response" + e.getMessage());
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
        }
      } catch (IOException e) {
        System.out.println("Stasrv: IOException setting up socket to " + host + "/" + port + " loop=" + loop + " " + e.getMessage());
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
          timedout = true;
          break;
        }// Marked timed out
      }
    }
    String resp = send(sb.toString());
    return resp;
  }

  /**
   * get Orientation of the channel
   *
   * @param net network code
   * @param stat station code
   * @param loc location code
   * @param chan channel code
   * @return 3 element array 0=aszimuth in degress clockwize from north, 1=dip in degrees from
   * horizontal, 2=depth in meters from surface.
   */
  public double[] getOrientation(String net, String stat, String loc, String chan) {
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
    Util.init("edge.prop");
    Util.setModeGMT();

    // If args present, use mdget mode
    if (args.length > 0) {
      String host = "cwbpub.cr.usgs.gov";
      int port = 2052;
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("-h")) {
          host = args[i + 1];
        }
        if (args[i].equals("-p")) {
          port = Integer.parseInt(args[i + 1]);
        }

      }
      StaSrv srv = new StaSrv(host, port);
      Util.prt(srv.mdget(args));
      System.exit(0);
    } else {
      Util.prt(mdgetHelp);
      System.exit(0);
    }
    StaSrv srv = new StaSrv(null, 2052);
    StringBuilder sb = new StringBuilder(10000);
    String[] ans = srv.getMetaStation("3AL002", "all");
    String site = srv.getSiteDescription("3AL002", "all");
    for (String s : ans) {
      Util.prt(s);
    }
    srv.doSelect("SELECT a,d,s,l,latitude,longitude,elevation,seff,send,sitename FROM irserver.epochs WHERE s='DUG'", sb);
    Util.prt(sb);
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
        System.out.println(" [" + i + "]=" + orient[i]);
      }
    }
    double[] coord = srv.getCoord("RO", "BUR01", "  ");
    PNZ pnz = srv.getCookedResponse("US", "BOZ", "  ", "BHZ");
    Util.prt("SAC format\n" + pnz.toSACString("nm"));
    System.out.println("lat=" + coord[0] + " long=" + coord[1] + " elev=" + coord[2]);
    System.out.println(pnz.toString() + " amp 1 hz=" + pnz.getAmp(1.) + " ph=" + pnz.getPhase(1.)
            + " .01=" + pnz.getAmp(0.01) + " ph=" + pnz.getPhase(0.01));
    orient = srv.getOrientation("US", "GOGA", "  ", "BHZ");
    if (orient != null) {
      for (int i = 0; i < orient.length; i++) {
        System.out.println(" [" + i + "]=" + orient[i]);
      }
    }
    orient = srv.getOrientation("IR", "ISCO", "  ", "BHNS");
    if (orient != null) {
      for (int i = 0; i < orient.length; i++) {
        System.out.println(" [" + i + "]=" + orient[i]);
      }
    }
    orient = srv.getOrientation("US", "ECSD", "HR", "BHE");
    if (orient != null) {
      for (int i = 0; i < orient.length; i++) {
        System.out.println(" [" + i + "]=" + orient[i]);
      }
    }
    orient = srv.getOrientation("US", "KSU1", "HR", "BHZ");
    if (orient != null) {
      for (int i = 0; i < orient.length; i++) {
        System.out.println(" [" + i + "]=" + orient[i]);
      }
    }
    orient = srv.getOrientation("US", "KSU1", "HR", "BH2");
    if (orient != null) {
      for (int i = 0; i < orient.length; i++) {
        System.out.println(" [" + i + "]=" + orient[i]);
      }
    }
    orient = srv.getOrientation("US", "KSU1", "HR", "BH1");
    if (orient != null) {
      for (int i = 0; i < orient.length; i++) {
        System.out.println(" [" + i + "]=" + orient[i]);
      }
    }
    pnz = srv.getCookedResponse("IR", "SDDR", "  ", "BHZ");
    System.out.println(pnz.toString() + " amp 1 hz=" + pnz.getAmp(1.) + " ph=" + pnz.getPhase(1.)
            + " .01=" + pnz.getAmp(0.01) + " ph=" + pnz.getPhase(0.01));
    System.out.println("tran IRSDDR=" + srv.getTranslation("IR", "SDDR", "  ", "BHZ")
            + " Ill=" + srv.getTranslation("IR", "SDDR", "  ", "BHZ"));
    ans = srv.getComment("IR", "GWDE", "  ");
    System.out.println("0=" + ans[0] + " 1=" + ans[1]);
    coord = srv.getCoord("US", "IIII", "  ");
    System.out.println("lat=" + coord[0] + " long=" + coord[1] + " elev=" + coord[2]);
    coord = srv.getCoord("IR", "GWDE", "  ");
    System.out.println("lat=" + coord[0] + " long=" + coord[1] + " elev=" + coord[2]);
  }
}
