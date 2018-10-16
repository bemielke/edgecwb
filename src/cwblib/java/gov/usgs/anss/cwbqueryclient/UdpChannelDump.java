/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.util.Util;

/**
 * This is a simple client that connects to a UdpChannel service and dumps the contents. It has
 * switches to allow selection of the server and port and to filter the channels by full regular
 * expressions. It can also be setup to repeatedly do the query after a certain interval.
 *
 * <PRE>
 * switch    Args          Description
 * -h      ip.adr         Use this host for the UdpChannel services (def=propert 'StatusServer') from chandisp.prop
 * -p      port           Use this port for UdpChannel services (def=7992)
 * -re     regexp         A regular expression to use on the NNSSSSSCCCLL to filter then channels dumped
 * -i      interval       The interval to wait between dumps if the -r is selected (def=5 seconds)
 * -r      #repeat        The number of times to repeat the query (def=0)
 * -lat                   Used by CWBQuery to run this program and not used here.
 *
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class UdpChannelDump {

  /**
   * Unit test main
   *
   * @param args the command line arguments
   */

  public static void main(String[] args) {
    Util.setModeGMT();            // All time calculation in GMT
    Util.init("chandisp.prop");   // Load the users property files to setup host
    String host = Util.getProperty("StatusServer"); // get the users .prop setting for the host
    if (host == null) {
      host = "localhost";            // If none, default to localhost
    }
    String usage = "Usage: -re regexp(use .* for every channel)[-h host][-p port][-r repeat][-i interval][-dbg]";
    boolean dbg = false;
    int port = 7992;
    String match = ".*";
    int repeat = 0;
    int interval = 5000;
    if (args.length < 2) {
      System.out.println(usage);
      System.exit(0);
    }

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-h":
          host = args[i + 1];
          i++;
          break;
        case "-p":
          port = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-re":
          match = args[i + 1];
          i++;
          break;
        case "-r":
          repeat = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-i":
          interval = Integer.parseInt(args[i + 1]) * 1000;    // in millis
          break;
        case "-?":
          System.out.println(usage);
          System.exit(1);
        case "-lat":
          break;
        default:
          System.out.println("Unkown argument : " + args[i]);
      }
    }
    // Startup the StatusSocketReader which attaches to UdpChannel and maintains all of the structures
    StatusSocketReader t = new StatusSocketReader(ChannelStatus.class, host, port);
    int lastNmsgs = -1;
    for (;;) {   // Wait for channels structure to be filled.
      Util.sleep(100);
      if (t.length() == lastNmsgs) {
        break;
      }
      lastNmsgs = t.length();
    }
    System.out.println(t.toString());
    Object[] objs = new Object[t.length() * 2];  // Make an array for us to hold the contents
    for (int ii = 0; ii < Math.max(repeat, 1); ii++) {  // for each repeat
      t.getObjects(objs);           // Get the objects from the StatusSocketReader into our array
      int len = t.length();         // Set the length of the array
      if (dbg) {                     // -dbg always dumps ALL of the channels.
        for (int i = 0; i < len; i++) {
          System.out.println(i + "=" + objs[i].toString());
        }
      }
      if (match != null) {                 // If there is a regexp, then use it
        System.out.println("#  Channel           Packet Time               Received time"
                + "#samp  Rate   Lrcv-min    Latency(s)");
        for (int i = 0; i < len; i++) {
          ChannelStatus st = (ChannelStatus) objs[i];
          if (st.getKey().matches(match)) //Does this channel match?
          {
            System.out.println(i + " " + st.getKey() + " " + Util.ascdatetime2(st.getPacketTime()) + " rcved at " + Util.ascdatetime2(st.getTime())
                    + " ns=" + st.getNsamp() + " rt=" + Util.df23(st.getRate()) + " age=" + Util.df21(st.getAge())
                    + " ltcy=" + Util.df21(st.getLatency()));
          }
        }
      }
      if (ii < repeat - 1) {
        Util.sleep(interval);
      }
    }
    System.out.println("End of execution");
  }
}
