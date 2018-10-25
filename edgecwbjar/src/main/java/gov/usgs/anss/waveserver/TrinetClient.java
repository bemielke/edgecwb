/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.trinet.waveserver.rt.WaveClientNew;
import org.trinet.waveserver.rt.WaveClient;
import org.trinet.waveserver.rt.TimeRange;
import org.trinet.waveserver.rt.Channel;

/**
 * This is a sample client to talk to a TrinetWaveServer. It was used to debug
 * the Trinet WS functions in the QueryMom. The -? option is most useful!
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class TrinetClient extends Thread {

  private static final Random random = new Random();
  private static String host = "localhost";
  private static int port = 2063;
  private final int nrequests;
  private final long millisBack;
  private long elapse;
  private int npacket;

  public static void setHost(String h) {
    host = h;
  }

  public static void setPort(int p) {
    port = p;
  }

  public long getElapse() {
    return elapse;
  }

  public int getNpacket() {
    return npacket;
  }

  public TrinetClient(long millisBack, int nrequests) {
    this.millisBack = millisBack;
    this.nrequests = nrequests;
    start();
  }

  @Override
  public void run() {
    WaveClientNew wclient = new WaveClientNew();
    wclient.addServer(host, port);
    wclient.setMaxTimeoutMilliSecs(180000);
    java.util.Date now = new java.util.Date();
    long begin = System.currentTimeMillis();
    for (int i = 0; i < nrequests; i++) {
      long start = System.currentTimeMillis() - millisBack + (long) (random.nextDouble() * 3600000);
      now.setTime(start);
      List packets = wclient.getPacketData("CI", "ISA", "BHZ", now, 120);
      if (packets != null) {
        Util.prta("return=" + packets.size() + " tim=" + now);
        npacket += packets.size();
        try {
          for (Object packet : packets) {
            Util.prt("" + new MiniSeed((byte[]) packet));
          }
        } catch (IllegalSeednameException e) {
          Util.prt("Illegal seed e=" + e);
        }
      } else {
        Util.prta("return=null " + now);
      }

    }
    elapse = System.currentTimeMillis() - begin;
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    WaveClient wclient;
    Util.init();
    /*TestWaveClient test= new TestWaveClient(3600000, 100);
    Util.prta("Starting");
    while(test.isAlive()) Util.sleep(100);
    Util.prta("End "+test.getElapse()+" #pck="+test.getNpacket());
    while(!test.isAlive() ) Util.sleep(1000);*/
 /*byte [] cc = {0x41,0x5a, 0x00,0x42, 0x5a,0x4e, 0,0,(byte)0xb8, 0x42,0x48,0x45,
    0,0x2d,0x2d,0,0,0,0,0,0,0,0x40,0x44,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0x3,0,0,0,0,0,0,0,0x8,0,0,0,0,0,0,0,(byte)0x88,0x11,0x62,0x79,
    0x3c,0,0,0
    };
    double [] vals = {40.,33.4915,-116.6670, 1301.};
    byte [] b = new byte[8];
    ByteBuffer bb = ByteBuffer.wrap(b);
    StringBuilder sb = new StringBuilder(100);
    ByteBuffer bc = ByteBuffer.wrap(cc);
    bc.position(22);
    for(int i=0; i<8; i++) {
      int pos = bc.position();
      int i1 = bc.getInt();
      int i2 = bc.getInt();
      bc.position(pos);
      double d = bc.getDouble();
      sb.append(i).append(" ").append(Util.toHex(i1)).append(" ").append(Util.toHex(i2)).append(" ").append(d).append("\n");
    }
    for(int i=0; i<vals.length; i++) {
      bb.position(0);
      bb.putDouble(vals[i]);
      sb.append(Util.rightPad(""+vals[i], 10));
      for(int j=0; j<8; j++) sb.append(Util.leftPad(Util.toHex(b[j]).toString().replaceAll("0x",""),4));
      sb.append("\n");
      
    }
    Util.prt(sb.toString());*/
    host = "localhost";
    port = 9101;
    //port=6000;
    //port = 2063;
    //port=9101;
    String propertyFile = null;
    String channel = "CIPASC BHZ00";
    int duration = 300;
    java.util.Date now = new java.util.Date();
    now.setTime(System.currentTimeMillis() - 300000L);
    String command = "GETDATA";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-prop")) {
        propertyFile = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-h")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-s")) {
        channel = args[i + 1].replaceAll("-", " ");
        i++;
      } else if (args[i].equalsIgnoreCase("-c")) {
        command = args[i + 1].replaceAll("-", " ");
        i++;
      } else if (args[i].equalsIgnoreCase("-d")) {
        duration = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-b")) {
        now = Util.stringToDate2(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-?")) {
        Util.prt("[-c GETDATA|GETTIMES|GETCHANS][-s NNSSSS-CCCLL][-b yyyy/mm/dd-hh:mm:ss][-d dur][-h host][-p port][ -prop file]");
        Util.prt("Defaults are -c " + command + " -s " + channel + " -b " + Util.ascdatetime2(now.getTime()) + " -d " + duration + " -h " + host + " -p " + port);
        System.exit(1);
      }
    }
    if (propertyFile != null) {
      wclient = new WaveClientNew(propertyFile);

    } else {
      wclient = new WaveClientNew();
      wclient.addServer(host, port);
      wclient.setMaxTimeoutMilliSecs(180000);
    }
    int nret;
    Util.prta(wclient.toString() + " " + wclient.listServers()[0] + " " + wclient.numberOfServers());
    //if(wclient == null) {Util.prta(" Failed to make WaveClient"); System.exit(1);}
    //Util.prt(" CI.ISA.BHZ SampleRate="+wclient.getSampleRate("CI","ISA","BHZ"));
    //Util.prt(" CI.ISA2.BHZ SampleRate error="+wclient.getSampleRate("CI","ISA2","BHZ"));

    if (command.equalsIgnoreCase("GETDATA") || command.equalsIgnoreCase("ALL")) {
      Util.prt("Try to get data for " + channel + " time " + now + " for " + duration + " seconds");
      List packets = wclient.getPacketData(channel.substring(0, 2).trim(), channel.substring(2, 7).trim(),
              channel.substring(7, 10).trim(), channel.substring(10),
              now, duration);
      if (packets == null) {
        Util.prt("Packets is null.  No data was returned.");
      } else {
        Util.prt("Packet size=" + packets.size());
        try {
          for (int i = 0; i < packets.size(); i++) {
            String gap = "";
            MiniSeed ms = new MiniSeed((byte[]) packets.get(i));
            if (i > 0) {
              MiniSeed ms2 = new MiniSeed((byte[]) packets.get(i - 1));
              if (ms2.getNextExpectedTimeInMillis() - ms.getTimeInMillis() > 1000 / ms.getRate()) {
                gap = "*** " + (ms2.getNextExpectedTimeInMillis() - ms.getTimeInMillis());
              }
            }
            Util.prt(i + " " + ms + gap);
          }
        } catch (IllegalSeednameException e) {
          Util.prt("Illegal seed e=" + e);
        }
      }
    }

    if (command.equalsIgnoreCase("GETTIMES") || command.equalsIgnoreCase("ALL")) {
      /* do times command */
      ArrayList<TimeRange> times = new ArrayList<>(1000);
      nret = wclient.getTimes("CI", "ISA", "BHZ", times);
      if (nret != WaveClient.TN_SUCCESS) {
        Util.prt("getTimes failed = " + nret);
      }
      Util.prt(" getTimes nret=" + nret + " size=" + times.size() + " " + times.get(0));
    }

    if (command.equalsIgnoreCase("GETCHANS") || command.equalsIgnoreCase("ALL")) {
      // Do channel command
      ArrayList<Channel> chans = new ArrayList<>(1000);
      nret = wclient.getChannels(chans);
      if (nret != WaveClient.TN_SUCCESS) {
        Util.prt("getChannels failed = " + nret);
      }
      Util.prt("getChannels nret=" + nret + " class=" + chans.get(0).getClass().getCanonicalName() + " " + chans.get(0));
      for (int i = 0; i < 10; i++) {
        Util.prt(i + " " + chans.get(i));
      }
    }
    wclient.close();
  }
}
