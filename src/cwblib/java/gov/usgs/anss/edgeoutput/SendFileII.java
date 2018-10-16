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
import java.io.File;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;

/**
 * This class implements the sending of a file using the EW sendfile protocol 1) 6 characters with
 * length of filename 2) the filename 3) repeating blocks with 6 characters with length of block 4)
 * Block contents 6) last block is indicated by zero length. 7) test transfer by reading 3 bytes
 * from socket and looking for "ACK".
 *
 *
 * @author davidketchum
 */
public final class SendFileII {

  public static final DecimalFormat df6 = new DecimalFormat("000000");
  public static boolean dbg = false;
  public static EdgeThread par;

  public static void setDebug(boolean t) {
    dbg = true;
  }

  public static void setLog(EdgeThread p) {
    par = p;
  }

  private static void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * Send a file currently stored in a string to a GetFileII.
   *
   * @param ip IP address of GetFileII
   * @param port Port for GetFileII
   * @param filename Filename to associate on the GetFileII end
   * @param contents The String containing the contents of the file
   * @return true, if transfer was successful
   * @throws UnknownHostException If the GetFileII host does not exist
   * @throws IOException If an IO error trying to open the socket, usually firewall or other issue.
   */
  public static boolean sendFile(String ip, int port, String filename, String contents) throws UnknownHostException, IOException {
    return sendFile(ip, port, filename, contents.getBytes(), contents.length());
  }

  /**
   * Send a file currently stored in a byte to a GetFileII. This is the routine that is called by
   * all of the other forms of sendFile to actually do the transfer.
   *
   * @param ip IP address of GetFileII
   * @param port Port for GetFileII
   * @param filename Filename to associate on the GetFileII end
   * @param b The byte array containing the contents to send
   * @param len The length of the file in bytes
   * @return true, if transfer was successful
   * @throws UnknownHostException If the GetFileII host does not exist
   * @throws IOException If an IO error trying to open the socket, usually firewall or other issue.
   */
  public static boolean sendFile(String ip, int port, String filename, byte[] b, int len)
          throws UnknownHostException, IOException {
    boolean sent = false;
    int attempts = 0;
    while (!sent && attempts < 4) {
      attempts++;
      if (dbg) {
        prta("SFII: Open socket to " + ip + ":" + port + " for " + filename + " len=" + len);
      }
      Socket d = new Socket(ip, port);
      int flen = filename.trim().length();
      try {
        if (dbg) {
          prta("SFII: Start write of # bytes and filename " + ip);
        }
        d.getOutputStream().write(df6.format(flen).getBytes());
        d.getOutputStream().write(filename.trim().getBytes());

        int off = 0;
        while (len > 0) {
          int l = len;
          if (l > 4096) {
            l = 4096;
          }
          if (dbg) {
            prta("SFII: Start write of bytes of " + l + " bytes offset=" + off + " " + ip);
          }
          d.getOutputStream().write(df6.format(l).getBytes());
          d.getOutputStream().write(b, off, l);
          off += l;
          len -= l;
        }
        if (dbg) {
          prta("SFII: Start write of ack at end " + ip);
        }
        d.getOutputStream().write(df6.format(0).getBytes());
        byte[] ack = new byte[3];
        if (dbg) {
          prta("SFII: Read back ack started " + ip);
        }
        boolean ok = false;
        for (int i = 0; i < 10; i++) {
          if (d.getInputStream().available() >= 3) {
            ok = true;
            break;
          }
          Util.sleep(250);
        }
        if (dbg && !ok) {
          prta("SFII: did not get ACK timed out attempt=" + attempts);
        }
        if (ok) {
          d.getInputStream().read(ack);
        }
        d.close();
        if (dbg) {
          prta("SFII: Close socket and return " + ip);
        }
        if (ack[0] == 'A' && ack[1] == 'C' && ack[2] == 'K') {
          sent = true;
        }
      } catch (IOException e) {
        prta("SFII: Send was not successful IO error=" + e + " try again. ip=" + ip + "/" + port + " " + filename);
        Util.sleep(1000);
      }
    }
    return sent;
  }

  /**
   * Send a file currently stored as a file to a GetFileII.
   *
   * @param ip IP address of GetFileII
   * @param port Port for GetFileII
   * @param filename Filename to associate on the GetFileII end
   * @return true, if transfer was successful
   * @throws UnknownHostException If the GetFileII host does not exist
   * @throws IOException If an IO error trying to open the socket, usually firewall or other issue.
   */
  public static boolean sendFile(String ip, int port, String filename) throws UnknownHostException, IOException {
    File f = new File(filename);
    if (f.exists()) {
      RawDisk raw = new RawDisk(filename, "r");
      int len = (int) raw.length();
      byte[] contents = new byte[len];
      raw.readBlock(contents, 0, len);
      return sendFile(ip, port, f.getName(), contents, len);
    } else {
      Util.prt("SFII: File does not exist " + filename);
    }
    return false;
  }
  public static final String help = "Usage : SendFileII -h hostIP -p port -path outputpath -f filename";

  public static void main(String[] args) {
    String ip = "";
    int port = 0;
    String filename = "";
    String outputpath = "";
    if (args.length == 0) {
      Util.prt(help);
      System.exit(0);
    }
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-h":
          ip = args[i + 1];
          i++;
          break;
        case "-p":
          port = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-f":
          filename = args[i + 1];
          i++;
          break;
        default:
          Util.prt("Unknown argument@" + i + " " + args[i] + "\n" + help);
          System.exit(0);
      }
    }
    try {
      boolean ok = sendFile(ip, port, filename);
      Util.prt("File was transferred.");
    } catch (UnknownHostException e) {
      Util.prt("The host was not found h=" + ip);

    } catch (IOException e) {
      Util.prt("IO error opening socket to GetFileII " + ip + "/" + port + " e=" + e);
    }
  }
}
