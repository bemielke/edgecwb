/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author davidketchum
 */
public final class SetRTSIPUtility {

  public static void main(String[] args) {
    try {

      Util.init("query.prop");
      Util.debug(false);
      Util.setModeGMT();
      Util.setProcess("SetRTSIPUtility");
      Util.setNoconsole(false);
      Util.setNoInteractive(true);
      Util.prt("Starting");
      // We need a connection to anss
      DBConnectionThread jcjbl = new DBConnectionThread("DBServer", "readonly", "anss", false, false, "anss", Util.getOutput());
      Statement stmt = jcjbl.getConnection().createStatement();
      byte[] buf = new byte[60];
      String station = null;
      String oldIP = null;
      ByteBuffer bb = ByteBuffer.wrap(buf);
      if (args.length >= 2) {
        for (int i = 0; i < args.length; i++) {
          switch (args[i]) {
            case "-s":
              station = args[i + 1];
              i++;
              break;
            case "-ip":
              oldIP = args[i + 1];
              i++;
              break;
            default:
              Util.prt("   ****** Unknown command line argument=" + args[i] + " exit with no action!");
              System.exit(1);
          }
        }
        Util.prt(station + " from " + oldIP);
      }
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      try {
        DatagramSocket d = new DatagramSocket();

        String line;
        while (true) {
          if (station == null) {
            Util.prt("Enter RTS station name (you must have put its new address, mask in Anss.jar)");
            line = in.readLine();
          } else {
            line = station;
          }
          if (line == null || line.equals("")) {
            System.exit(0);
          }
          ResultSet rs = stmt.executeQuery("SELECT * FROM tcpstation WHERE tcpstation='" + line + "'");
          if (rs.next()) {
            String ip = rs.getString("ipadr");
            String netmask = rs.getString("ipmask");
            InetAddress inet = InetAddress.getByName(ip);
            InetAddress mask = InetAddress.getByName(netmask);
            byte[] ipbyte = inet.getAddress();
            byte[] msk = mask.getAddress();
            for (int i = 0; i < 4; i++) {
              ipbyte[i] = (byte) (ipbyte[i] & msk[i]);
            }
            ipbyte[3] += 1;
            int[] ipint = new int[4];
            for (int i = 0; i < 4; i++) {
              ipint[i] = ((int) ipbyte[i]) & 0xff;
            }
            if (oldIP == null) {
              Util.prt("Enter IP address where RTS is currently located");
              line = in.readLine();
            } else {
              line = oldIP;
            }
            InetAddress rtsaddr = InetAddress.getByName(line);
            Util.prt("RTS at IP=" + rtsaddr + " move to IP=" + ip + " netmask=" + netmask + " gateway=" + ipint[0] + "." + ipint[1] + "." + ipint[2] + "." + ipint[3] + ".");
            Util.prt("Are your sure? (y/n)");
            line = in.readLine();
            if (line.length() <= 0) {
              continue;
            }
            if (line.charAt(0) == 'y') {
              Util.prt("Starting move.  This will take 4-10 seconds");
              bb.position(0);
              bb.put((byte) 33);
              bb.put((byte) 3);
              bb.put((byte) 17);      // reset RTS IP and gateway command
              bb.put((byte) 24);
              bb.putInt(0x1a2b3c4d);
              bb.putInt(0x4d3c2b1a);
              bb.put(inet.getAddress());
              bb.put(mask.getAddress());
              bb.put(ipbyte);

              DatagramPacket p = new DatagramPacket(buf, bb.position(), rtsaddr, 7999);
              d.send(p);
              Util.sleep(2000);
              d.send(p);
              Util.sleep(2000);
              buf[2] = 3;
              buf[3] = 4;
              p.setData(buf, 0, 4);
              d.send(p);
              Util.sleep(2000);
              d.send(p);
              if (station != null) {
                System.exit(0);
              }
            }
          } else {
            Util.prt("Station not found");
          }
          Util.prt("Enter station name");
        }
      } catch (IOException e) {
        Util.IOErrorPrint(e, "Readin a line");
      }
    } catch (InstantiationException e) {
      Util.prt("Weird instantiation exception ");
      e.printStackTrace();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Looking at tcpstation table");
    }
  }
}
