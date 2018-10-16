/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.Socket;

/**
 *
 * @author davidketchum
 */
public final class TextStatusClient extends Thread {

  private final int port;
  private final String ipadr;
  private final StringBuilder sb = new StringBuilder(10000);
  ;
  private final String cmd;
  private final String body;
  private Socket s;
  private boolean terminate;

  @Override
  public String toString() {
    return ipadr + "/" + port + " isAlive=" + isAlive() + " cmd=" + cmd + " s=" + s + " ret.len=" + sb.length();
  }

  public void terminate() {
    terminate = true;
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
  }

  public String getText() {
    return sb.toString();
  }

  public int length() {
    return sb.length();
  }

  public String getText(String glob) {
    StringBuilder ret = new StringBuilder(sb.length());
    String line;
    int nline = 0;
    int match = 0;
    String regexp;
    if (glob.contains("*")) {
      regexp = globToRegex(glob);
    } else {
      regexp = "";
    }
    try {
      BufferedReader in = new BufferedReader(new StringReader(sb.toString()));
      while ((line = in.readLine()) != null) {
        nline++;
        if (glob.length() == 0) {    // no reg exp so just send it
          ret.append(line).append("\n");
        } else if (regexp.equals("***")) {
          if (line.contains(regexp)) {
            ret.append(line).append("\n");
            match++;
          }
        } else if (regexp.equals("")) {
          if (line.contains(glob)) {
            ret.append(line).append("\n");
            match++;
          }
        } else if (line.matches(regexp)) {
          ret.append(line).append("\n");
          match++;
        }
      }
      return ret.toString() + "\n" + match + " matches out of " + nline + "\n";
    } catch (IOException e) {
      Util.prt("TSC IO exception doing regular expression" + regexp);
    }
    return ret.toString() + "\n" + match + " matches out of " + nline + "\n";
  }

  /**
   * Convert a glob to a regular expression string.
   *
   * The syntax for globs is based on that of the Unix shell. '*' matches any number of characters.
   * '?' matches any single character. '[abc]' matches 'a', 'b', or 'c'. '[^abc]' matches any
   * character but 'a', 'b', and 'c'. Special characters can be escaped by a backslash.
   *
   * @param glob a string containing a glob pattern
   * @return a string containing a regular expression
   * @throws IllegalArgumentException if the glob is invalid in some way (unmatched brackets,
   * premature end of string, etc.)
   */
  public static String globToRegex(String glob) {
    int i;
    char c;
    int charClass;
    StringBuilder regex;

    regex = new StringBuilder();
    charClass = -1;
    if (glob.equals("***")) {
      return "***";
    }
    for (i = 0; i < glob.length(); i++) {
      c = glob.charAt(i);
      if (c == '*') {
        regex.append(".*");
      } else if (c == '?') {
        regex.append(".");
      } else if (c == '\\') {
        i++;
        if (i >= glob.length()) {
          throw new IllegalArgumentException("End of string while processing '\\' at position " + (i - 1) + " in glob \"" + glob + "\"");
        }
        c = glob.charAt(i);
        if (Character.isLetter(c)) {
          regex.append(c);
        } else {
          regex.append("\\").append(c);
        }
      } else if (c == '[') {
        charClass = i;
        regex.append(c);
      } else if (c == ']') {
        charClass = -1;
        regex.append(c);
      } else if (c == '^' && charClass >= 0) {
        regex.append(c);
      } else if (!Character.isLetter(c)) {
        regex.append("\\").append(c);
      } else {
        regex.append(c);
      }
    }

    if (charClass >= 0) {
      throw new IllegalArgumentException("End of string while reading character class that started at position " + charClass + " in glob \"" + glob + "\"");
    }

    return regex.toString();
  }

  /**
   * Creates a new instance of TextStatusClient
   *
   * @param ip IP address of target command server
   * @param pt Port of target command server
   * @param command Command to execute
   */
  public TextStatusClient(String ip, int pt, String command) {
    port = pt;
    ipadr = ip;
    cmd = command;
    body = "";
    //sb.append("Command output :\n");
    start();
  }

  /**
   * Creates a new instance of TextStatusClient
   *
   * @param ip IP address of target command server
   * @param pt Port of target command server
   * @param command Command to execute
   * @param bod If the command is not a one liner, use this to send the rest of the body
   */
  public TextStatusClient(String ip, int pt, String command, String bod) {
    port = pt;
    ipadr = ip;
    cmd = command;
    body = bod;
    //sb.append("Command output :\n");
    start();
  }

  @Override
  public void run() {
    try {
      s = new Socket(ipadr, port);
      Util.prt("Socket opened to " + ipadr + "/" + port + " s=" + s + " cmd=" + cmd);
      sb.append(Util.asctime()).append(" Try to open ").append(ipadr).append("/").append(port);
      sb.delete(0, sb.length());
      InputStream in = s.getInputStream();
      int len;
      byte[] buf = new byte[1000];
      if (!cmd.equals("")) {
        s.getOutputStream().write((cmd + "\n" + System.getProperty("user.name") + "\n").getBytes());
        if (body.length() > 0) {
          s.getOutputStream().write(body.getBytes());
        }
      }
      for (;;) {
        len = in.read(buf);
        if (len == -1 || terminate) {
          break;
        }
        //Util.prta("append string length="+len+" "+new String(buf, 0, len));
        sb.append(new String(buf, 0, len));
        if (sb.length() > 30000) {
          sb.delete(0, 5000);    // Keep only 30000 character max
        }
      }
    } catch (IOException e) {
      if (s != null) {
        if (s.isClosed()) {
          Util.prt("socket closed terminate=" + terminate);
        } else {
          Util.prt("IOError to " + ipadr + "/" + port + " " + e.getMessage());
          sb.append("IOError to ").append(ipadr).append("/").append(port).append(" ").
                  append(e.getMessage()).append("\n\n is CommandStatusServer down on this node!\n");
        }
      } else {
        sb.append("Failed to open socket to ").append(ipadr).append("/").append(port).append(" e=").append(e).append("\n");
      }
    }
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
    // we have exited
  }

}
