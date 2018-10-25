/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.net.UdpQueuedServer;
import gov.usgs.anss.util.Util;
import java.net.UnknownHostException;

/**
 * This class receives UDP packets for the alarm system and passes them to the
 * HandleEvents threads which implement the paging and email. This in
 * instantiated from Alarm, but is not called by anything else.
 *
 * <br>
 * <br> offset Description
 * <br> 0,1,2,3 33,3,0,201 A header for event types
 * <br> 4 process - The local system process(20 char) normally last 4 of the
 * account, a space, and up to 15 of process.
 * <br> 24 Source - The source of the message (12 char)
 * <br> 36 code - the event code for this source (12 char)
 * <br> 48 Phrase or payload (80 Char)
 * <br> 128 Node (char 12) encodes as role:servername and truncated to 12.
 * <br> 140 End of data
 *
 * @author davidketchum
 */
public final class UdpEventProcess extends EventInputer {

  private final UDPEPShutdown shutdown;
  private final int overrideUdpPort;
  private final boolean replicate;
  private final String replicateIP;
  private final int replicatePort;
  private final int replicateBindPort;
  private int nmsg;
  UdpQueuedServer udpin = null;

  public UdpQueuedServer getUdpQueuedServer() {
    return udpin;
  }

  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * Creates a new instance of UdpEventProcess
   *
   * @param overrideUdpPort If zero, use default or AlarmPort property, but is non-zero use this receiver port
   * @param rep If true, replication is on
   * @param ip The replication target IP address (where we are going to send the
   * UDP)
   * @param pt The replication target port (the port where its going)
   * @param bindport The address to bind locally (normally to make firewalls
   * easier)
   * @param noDB If true, running in noDB mode - do not process the events just
   * forward or log them
   */
  public UdpEventProcess(int overrideUdpPort, boolean rep, String ip, int pt, int bindport, boolean noDB) {
    super(noDB);
    replicate = rep;
    replicateIP = ip;
    replicatePort = pt;
    replicateBindPort = bindport;
    this.overrideUdpPort = overrideUdpPort;
    this.noDB = noDB;
    Util.prta("UDPEventProcess: replication=" + replicate + " on " + replicateIP + "/" + replicatePort + " bind local port=" + replicateBindPort);
    shutdown = new UDPEPShutdown();
    Runtime.getRuntime().addShutdownHook(shutdown);
    SendEventRelay.setEventHandler(replicateIP, replicatePort, replicateBindPort);
    start();
  }

  @Override
  public void run() {
    byte[] buf = new byte[140];
    int len;
    String source;
    String code;
    String phrase;
    String node;
    String process;
    StringBuilder sourcesb = new StringBuilder(12);
    StringBuilder codesb = new StringBuilder(12);
    StringBuilder phrasesb = new StringBuilder(80);
    StringBuilder processsb = new StringBuilder(20);
    StringBuilder nodesb = new StringBuilder(12);

    try {
      if (Util.getNode().contains("ketchum3")) {
        udpin = new UdpQueuedServer("localhost", 7207, 1000, 140, 0);
      } else {
        int port = 7964;
        try {
          if(Util.getProperty("AlarmPort") != null) {
            port = Integer.parseInt(Util.getProperty("AlarmPort"));
          }
        }
        catch(RuntimeException e) {
          
        }
        if(overrideUdpPort > 0) {
          port = overrideUdpPort;
        }
        udpin = new UdpQueuedServer("", port, 1000, 140, 0);
      }
    } catch (UnknownHostException e) {
      Util.prta("Could not open udp port Unknown host=" + e.getMessage());
    }
    Util.prta("UDPEP: udpin = " + udpin);
    // This is the infinite loops
    while (!terminate && udpin != null) {
      try {
        // wait for a new packet, put it in buf
        while ((len = udpin.dequeue(buf)) < 0) {
          try {
            sleep(100);
          } catch (InterruptedException expected) {
          }
        }
        nmsg++;
        if (buf[0] != 33 || buf[1] != 3 || buf[2] != 0 || buf[3] != -55) {
          // This is not a header for an event, discard
          Util.prta("Got non-event int UdpEventProcess - discard =" + buf[0] + " " + buf[1] + " " + buf[2] + " " + buf[3]);
          continue;
        }

        for (int i = 4; i < buf.length; i++) {
          if (buf[i] == 0) {
            buf[i] = ' ';
          }
        }

        source = new String(buf, 24, 12);
        sourcesb = Util.bufToSB(buf, 24, 12, sourcesb);
        code = new String(buf, 36, 12);
        codesb = Util.bufToSB(buf, 36, 12, codesb);
        phrase = new String(buf, 48, 80);
        phrasesb = Util.bufToSB(buf, 48, 80, phrasesb);
        process = new String(buf, 4, 20);
        processsb = Util.bufToSB(buf, 4, 20, processsb);
        node = new String(buf, 128, 12);
        nodesb = Util.bufToSB(buf, 128, 12, nodesb);
        Util.prta("UDPEventProcess from " + udpin.getSockAddress() + " " + source.trim() + "-" + code.trim() + " "
                + node.trim() + "/" + process.trim() + " " + phrase.trim());

        if (!noDB) {
          handleEvent(source, code, phrase, process, node);
        }
        if (replicate) {
          SendEventRelay.sendEvent(source, code, phrase, node, process);
        }

      } catch (RuntimeException e) {
        Util.prt("RuntimeException in UdpEventProcess continue e=" + e);
        e.printStackTrace();
      }

    }
  }

  private final class UDPEPShutdown extends Thread {

    public UDPEPShutdown() {

    }

    @Override
    public void run() {
      terminate();
      Util.prta("UdpEventProcess is shutting down.");
    }
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setNoInteractive(true);
    boolean replicate = false;
    if (Util.getNode().indexOf("edge") == 0) {
      replicate = true;
    }
    UdpEventProcess udp = new UdpEventProcess(0, replicate, "localhost", 7207, 0, true);
    int loop = 0;
    for (;;) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException expected) {
      }
      //if(loop % 10 == 2) SendEventRelay.sendEvent("Testing","00002","This is the payload","nsn9","testproc");
      //else SendEventRelay.sendEvent("Testing","00001","This is the payload","nsn9","testproc");
      loop++;
      //try{Thread.sleep(100000);} catch(InterruptedException expected) {}
    }
  }
}
