/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * read in the /etc/hosts file and return answers about it. This does not seem to be used anymore.
 *
 * @author davidketchum
 */
public class EtcHosts {

  static ArrayList<Host> hosts;

  /**
   * Creates a new instance of EtcHosts
   */
  public EtcHosts() {
    hosts = new ArrayList<>(10);
    reread();
  }

  private void reread() {
    try {
      //Util.prt("read in /etc/hosts");
      hosts.clear();
      try (BufferedReader in = new BufferedReader(new FileReader(Util.fs + "etc" + Util.fs + "hosts"))) {
        String s;
        while ((s = in.readLine()) != null) {
          if (!s.substring(0, 1).equals("#")) {
            if (s.charAt(0) < '0' || s.charAt(0) > '9') {
              String[] a = s.split("\\s");
              //Util.prt("split="+a.length+"a[0]="+a[0]);

              hosts.add(new Host(a[0], a[1]));
              //Util.prt(hosts.size()+" h="+a[0]+" ip="+a[1]);
            } //else Util.prt("Not a system="+s);
          } //else Util.prt("Skip comment"+s);
        }
      }
    } catch (IOException e) {
      Util.prt("Could not open/read /etc/hosts");
    }
  }

  public static String getHost(int i) {
    return ((Host) hosts.get(i)).getHost();
  }

  public static String getIP(int i) {
    return ((Host) hosts.get(i)).getIP();
  }

  public static int getSize() {
    return hosts.size();
  }

  public static String findIPForHost(String h) {
    for (Host host : hosts) {
      if (((Host) host).getHost().equalsIgnoreCase(h)) {
        return ((Host) host).getIP();
      }
    }
    return "";
  }

  public static String findHostForIP(String h) {
    for (Host host : hosts) {
      if (((Host) host).getIP().equalsIgnoreCase(h)) {
        return ((Host) host).getHost();
      }
    }
    return "";
  }

  public final class Host {

    String dot;
    String host;

    public Host(String h, String d) {
      dot = d;
      host = h;
    }

    public String getHost() {
      return host;
    }

    public String getIP() {
      return dot;
    }

  }

  public static void main(String[] args) {
    Util.init();
    Util.prt("Starting");
    EtcHosts etc = new EtcHosts();
    int n = EtcHosts.getSize();
    for (int i = 0; i < n; i++) {
      Util.prt(i + " host=" + EtcHosts.getHost(i) + " ip=" + EtcHosts.getIP(i));
    }
    Util.prt("IP for host edge2=" + EtcHosts.findIPForHost("edgE2"));
    for (String arg : args) {
      Util.prt("Host for IP " + arg + "=" + EtcHosts.findHostForIP(arg));
    }
    etc.reread();
    try {
      Enumeration en = NetworkInterface.getNetworkInterfaces();
      while (en.hasMoreElements()) {
        NetworkInterface face = (NetworkInterface) en.nextElement();
        Util.prt("Interface name=" + face.getName() + " display=" + face.getDisplayName() + "\n" + face.toString());

      }
    } catch (SocketException e) {
      Util.prt("Get network interfaces failed");
    }
  }

}
