/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.EdgeBlock;
import gov.usgs.anss.edge.EdgeBlockPool;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.EdgeThreadTimeout;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.GregorianCalendar;
import java.util.TreeMap;

/**
 * This class subscribes to a EdgeBlockServer to: get all of the real time blocks from that node
 * submit all received blocks to a similar EdgeBlockQueue on this node (or at least this process on
 * a node). The blocks are resequenced as they come in, but could also be served out to yet another
 * node.
 * <p>
 * Instance based configuration - for convenience in instance based configuration this thread can be
 * configurated to go get an instance. The line for such looks like :
 * <pre>
 * -p 27#0 -h $INSTANCEIP-27#0,$INSTANCEFAILOVERIP-27#0 -csend -hydra -dbg >>ebqc27A
 * </pre> The special variables $INSTANCEIP-NN#I and $INSTANCEFAILOVERIP-NN#I are converted in the
 * instance configuration thread to the IP address where the instance normally runs and the instance
 * where the instance might be failed over. In this way this configuration does not have to change
 * on any CWB an instance even if the node is failed over or the fail over node changes. Note: this
 * does not do the right thing if the instance tag is actually changed if an instance moves
 * permanently to another role. The -p NN#I is translated to the expected port for this instance
 * (7500+NN*10+I).
 * <p>
 *
 * A second mode request is pointed for a particular julian day and node at an IndexFileServer. In
 * this case the blocks are read until EOF is found and then this thread terminates. This mode is
 * used to insure the IndexFile (.idx) is fully created by a Replicator when the file is open so
 * that the index checking can begin.
 *
 * <br>
 * NOTES : The EdgeBlockClients running on a CWB-CWB connection must have a -cwbcwb ip.adr on their
 * replicator. In Edge-to-CWB mode the address of the place to get Index files on startup are
 * obtained from the EdgeBlockClient going to that node. In CWB-CWB there are no node by node
 * EdgeBlockClients. There is just a client to the cwb. This replicator switch lets the replicator
 * know this is the case. To support instance based the "port" can be an instance and the hosts are
 * a list of hosts which might host this instance.
 *
 * <br>
 * <PRE>
 *It is EdgeThread with the following arguments :
 * Tag			arg						 Description
 * -h				ip.nn[,ip.nn]	 The dotted IP or DNS translatable server address[es] to contact for the blocks.
 * -p				pppp					 Port number of the server or an instance tag nn#i
 * -poff    nnnn           Reset the offsetPort for instance based configuration (def=7500)
 * -rb										 Set rollback startup mode true. If seq not given, play back 90% of servers buffer.
 * -seq			nnnnn					 Start with sequence given or the oldest data if it is out of range.
 * -csend									 If present, create a ChannelSender to send summaries to UdpChannel.
 * -hydra									 If present, send the data received to Hydra wire (not the normal mode).
 * -j				julian				 This is a indexMode where an index file is needed from the remote node (must use -node as well).
 * -node		4char					 This is the node to get the index file from in conjunction with mandatory -j julian.
 * -dbg										 Turn on debug output.
 * >[>]			filename			 You can redirect output from this thread to the filename in the current logging directory.
 * </PRE>
 *
 * @author davidketchum
 */
public final class EdgeBlockClient extends EdgeThread {

  public static long nrqstblks;
  private static Map<String, EdgeBlockClient> clients; // Index to all EdgeBlockClients with the remote "node" as key
  private static final EdgeBlockPool ebp = new EdgeBlockPool();
  private static ForceCheckServer forceCheck;
  private static final StringBuilder FORCELOADIT = new StringBuilder(12).append("FORCELOADIT!");
  private String socketIP;        // IP or the other end 
  private String[] socketIPs;    // a list of potential connections for this instance
  private int socketPort;         // Port of the other end 
  private int portOffset = 7500;    // default instance based port offset
  private boolean connected;      // True when socket is open to node
  private boolean eof;            // True if socket is closed with EOF
  private int nblks;
  private long maxReadMS=0;
  private long maxMaxReadMS;
  private int lastNblks;
  private long lastStatus;
  private Socket s;
  private int julian;
  private boolean indexBlockMode; // If true, this is a temporary client to get index blocks
  private ArrayList<EdgeBlock> indexBlockModeBlocks;
  private String node;            // The edge node this is connected to
  private String orgNode;         // Copy of command line node
  private InputStream in;
  private OutputStream outsock;
  private RequestWriter rwq;
  private ChannelSender csend;
  private boolean hydra;
  private final ShutdownEdgeBlockClient shutdown;
  private final GregorianCalendar today = new GregorianCalendar();
  private boolean rollback;
  private boolean dbg;
  private final byte[] configbuf;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public void setDebug(boolean t) {
    dbg = t;
  }

  public static boolean isShutdown() {
    if (clients == null) {
      return true;
    }
    Iterator<EdgeBlockClient> itr = clients.values().iterator();
    boolean result = true;
    while (itr.hasNext()) {
      if (itr.next().isRunning()) {
        result = false;
      }
    }
    return result;
  }

  /**
   * Close any socket to the EdgeBlockServer and interrupt the run() thread.
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
    try {
      if (s != null) {
        if (!s.isClosed()) {
          s.close();
        }
      }
    } catch (IOException e) {
    }
    prta("EBC: " + tag + " terminate requested");
  }

  public OutputStream getOutputStream() {
    return outsock;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("EBC: ").append(socketIP).append("/").append(socketPort).append(" run=").append(running);
    }
    return sb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    int nb = (nblks - lastNblks);
    lastNblks = nblks;
    int blksec = (int) (nb * 1000 / (Math.max(1, System.currentTimeMillis() - lastStatus)));
    lastStatus = System.currentTimeMillis();
    return statussb.append(socketIP).append("/").append(socketPort).append(" bk=").append(nb).
            append(" b/s=").append(blksec).append(" ").append(blksec * 548 * 8 / 1000).append(" kbps").
            append(" maxRdMS=").append(maxReadMS);
  }

  /**
   * Returns the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = nblks - lastMonBlks;
    lastMonBlks = nblks;
    return monitorsb.append("EdgeBlockClientNrecs-").append(node).append("=").append(nb).append("\n");
  }
  long lastMonBlks;

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  private RequestWriter getRequestWriter() {
    return rwq;
  }

  public String getSocketIP() {
    return socketIP;
  }

  public String getNode() {
    return node;
  }

  /**
   * Returns an edgeblock client when given a node (from a static list of such clients).
   *
   * @param nd The node key to use to look up the client.
   * @return The EdgeBlockClient for that node or null if none is found.
   */
  public static EdgeBlockClient getEdgeBlockClient(String nd) {
    if (clients == null) {
      return null;
    }
    return (EdgeBlockClient) clients.get(nd.trim());
  }

  /**
   * Does a request to this socket for the given blocks.
   *
   * @param jul	The julian date desired.
   * @param node	The node of the data file.
   * @param start	The starting block number in the data file.
   * @param end	The ending block number in the data file.
   * @param index	The index block number of the missing block.
   * @param extent The extent index offset of the missing blocks extent.
   * @return True if all sends were successful, fail if any of them are blocked.
   */
  public static boolean requestBlocks(int jul, String node, int start, int end, int index, int extent) {
    byte[] buf = new byte[28];
    ByteBuffer bb = ByteBuffer.wrap(buf);
    bb.put("RQST".getBytes());
    bb.putInt(jul);
    bb.put((node + "    ").substring(0, 4).getBytes());
    bb.putInt(start);
    bb.putInt(end);
    bb.putInt(index);
    bb.putInt(extent);
    nrqstblks += (end - start + 1);
    if (clients == null) {
      return false;
    }
    boolean ok = true;
    try {
      for (EdgeBlockClient ebc : clients.values()) {
        ok &= ebc.getRequestWriter().sendRequest(buf);
      }
    } catch (ConcurrentModificationException e) {
      Util.prta("ConcurrentModificationException handled in EdgeBlockClient.requestBlocks!");
    }
    return ok;
  }

  /**
   * When in indexBlockMode, wait for all blocks to be delivered or bust out after the given number
   * of milliseconds.
   *
   * @param ms The number of milliseconds to wait for the index.
   * @return An ArrayList of EdgeBlock with the index file.
   */
  public ArrayList<EdgeBlock> waitForIndex(int ms) {
    long start = System.currentTimeMillis();
    int lastSize = 0;
    while (connected) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }
      if (System.currentTimeMillis() - start > ms) {
        terminate();
        SendEvent.debugSMEEvent("IdxReadErr", SeedUtil.fileStub(julian) + "_" + orgNode + " eof=" + (eof ? "T" : "F") + " cn=" + (connected ? "T" : "F")
                + " sz=" + indexBlockModeBlocks.size() + " ms=" + (ms / 1000), "EdgeBlockClient");
        prta(Util.clear(tmpsb).append(tag).append("IdxReadErr *** ms=").append(ms).append(" eof=").append(eof ? "T" : "F").
                append(" conn=").append(connected ? "T" : "F").append(" sz=").append(indexBlockModeBlocks.size()).
                append(" ").append(SeedUtil.fileStub(julian)).append("_").append(orgNode).append(" ").
                append(socketIP));
        return null;
      }
      if (indexBlockModeBlocks.size() != lastSize) {
        start = System.currentTimeMillis();// reset the timer,progress
      }
      lastSize = indexBlockModeBlocks.size();
    }
    return indexBlockModeBlocks;
  }

  public void freeIndexBlocks() {
    for (int i = indexBlockModeBlocks.size() - 1; i >= 0; i--) {
      ebp.free(indexBlockModeBlocks.get(i));
    }
  }

  /**
   * Creates a new instance of EdgeBlockClient if the port is zero, default EDGE_BLOCK_SERVER is
   * used from AnssPorts. It is an EdgeThread with the following arguments :
   * <br> -h	The dotted IP or DNS translatable server address to contact for the blocks.
   * <br> -p	Port number of the server to contact to get the blocks.
   * <br> -rb	Set rollback startup mode to true. If seq not given, play back 90% of servers buffer.
   * <br> -seq nnnnn Start with the sequence given, or the oldest data if it is out of range.
   * <br> -dbg	Turn on debug output (ends up in the edgemom.log? file.
   * <br> >	You can redirect output from this thread to something other than edgemom.
   *
   * @param argline The command line from Edgemom.setup for local parsing.
   * @param tg The "tag" from the Edgemom.setup to use for this thread.
   */
  public EdgeBlockClient(String argline, String tg) {
    super(argline, tg);
    if (clients == null) {
      clients = (Map<String, EdgeBlockClient>) Collections.synchronizedMap(new TreeMap<String, EdgeBlockClient>());
    }
    lastStatus = System.currentTimeMillis();
    String[] args = argline.split("\\s");
    dbg = false;
    nblks = 0;
    socketPort = 0;
    int startseq = 0;
    hydra = false;
    prta(Util.clear(tmpsb).append("Start EBC argline=").append(argline));
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].substring(0, 1).equals(">")) {
        break;
      }
      switch (args[i]) {
        case "-h":
          socketIPs = args[i + 1].split(",");   // support a list of IPs.
          socketIP = socketIPs[0];
          i++;
          break;
        case "-p":
          if (args[i + 1].contains("#")) {
            socketPort = IndexFile.getNodeNumber(args[i + 1]) * 10 + IndexFile.getInstanceNumber(args[i + 1]) + portOffset;
          } else {
            socketPort = Integer.parseInt(args[i + 1]);
          }
          i++;
          break;
        case "-poff":
          portOffset = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-rb":
          rollback = true;
          break;
        case "-seq":
          startseq = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-node":
          node = args[i + 1].trim();
          i++;
          break;
        case "-j":
          julian = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-csend":
          csend = new ChannelSender("  ", "EdgeBlockClient", "EBC-" + tag);
          break;
        case "-hydra":
          hydra = true;
          break;
        default:
          prt(Util.clear(tmpsb).append("EBC: ").append(i).append(" unknown switch=").append(args[i]).append(" ln=").append(argline));
          break;
      }
    }
    tag = tag + "/" + socketPort;
    if (socketPort == 0) {
      socketPort = AnssPorts.EDGE_BLOCK_SERVER;
    }
    String tagtmp = (getTag().trim() + "          ").substring(0, 10);
    configbuf = new byte[22];
    ByteBuffer bf = ByteBuffer.wrap(configbuf);
    bf.clear();
    if (julian == 0) {       // This is a normal one to replicate data
      bf.putInt(startseq);
      bf.putInt((rollback ? 1 : 0));
      bf.put(tagtmp.getBytes());
      bf.put("    ".getBytes());     // indicate no node
    } else {          // This is an index file one
      if (node == null) {
        prta(Util.clear(tmpsb).append(tag).append("***** it is illegal to make a EBC with julian but no node!"));
      } else {
        EdgeBlockClient cl;
        if (socketIP == null) {    // must be getting data from a Edge, look up its IP by node
          cl = EdgeBlockClient.getEdgeBlockClient(node.trim());
          dbg = true;
          if (cl == null) {
            prta(Util.clear(tmpsb).append(tag).append("EdgeBlockClient julian mode does not have an existing EBC????"));
            throw new RuntimeException("EdgeBlockClient has not started to match this IFILE get! node=" + node + " " + SeedUtil.fileStub(julian));
          }
          socketIP = cl.getSocketIP();
          socketIPs = new String[1];    // Fake up the socketIps if in indexBlockMode.
          socketIPs[0] = socketIP;
        }
      }
      indexBlockModeBlocks = new ArrayList<>(5000);  // place to build up the IndexFile blocks
      rollback = false;
      orgNode = node;
      bf.putInt(julian);
      bf.putInt(0);
      bf.put(tagtmp.getBytes());
      bf.put((node + "    ").substring(0, 4).getBytes());
      indexBlockMode = true;
    }
    shutdown = new ShutdownEdgeBlockClient(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (forceCheck == null) {
      forceCheck = new ForceCheckServer(AnssPorts.EDGEMOM_FORCE_CHECK_PORT);
    }
    if (!forceCheck.isRunning()) {
      forceCheck = new ForceCheckServer(AnssPorts.EDGEMOM_FORCE_CHECK_PORT);
    }
    connected = true;
    running = false;
    prta(Util.clear(tmpsb).append(tag).append(" EBC: thread started IP=").append(socketIP).
            append(" port=").append(socketPort).append(" tg=").append(tg).append(" rl=").append(rollback).
            append(" indexBlockMode=").append(indexBlockMode).append(" jul=").
            append(julian == 0 ? "0" : SeedUtil.fileStub(julian) + "_" + node).append(" force=").append(forceCheck));
    if (indexBlockMode) {
      tag += " " + SeedUtil.fileStub(julian) + "_" + node + ":";
    }
    node = null;
    start();
  }

  /**
   * read in packets and put them in the EdgeBlockQueue
   */
  @Override
  public void run() {
    in = null;
    outsock = null;
    running = true;
    int err = 0;
    eof = false;
    EdgeBlock eb = new EdgeBlock();
    byte[] buf = new byte[EdgeBlock.BUFFER_LENGTH];
    // These are used if a channel sender is asked for
    byte[] msbuf = new byte[512];
    int[] time = new int[4];
    int millis;
    MiniSeed ms = null;
    StringBuilder runsb = new StringBuilder(50);
    StringBuilder msSeedname = new StringBuilder(12);
    long now = 0;
    long lastBlocks = 0;
    long lastStatusRun = System.currentTimeMillis();
    long nsleep = 0;
    long nroll = 0;
    long readMS = 0;

    // Create a timeout thread to terminate this guy if it quits getting data
    EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 120000, 300000);
    boolean forceLoadCheck = true;      // Mark we need to do a force load check after startup

    try {         // This catches runtime exceptions
      if (!indexBlockMode) {
        rwq = new RequestWriter();
      }
      while (!terminate) {
        int noconnect = 0;
        int nextIP = 0;
        // This loop establishes a socket
        while (!terminate) {
          try {
            socketIP = socketIPs[nextIP];
            nextIP = (++nextIP) % socketIPs.length;
            prta(Util.clear(runsb).append(tag).append(" EBC: try to create socket to EdgeBlockServer at ").
                    append(socketIP).append("/").append(socketPort).
                    append(" indexBlockMode=").append(indexBlockMode));
            s = new Socket(socketIP, socketPort);
            prta(Util.clear(runsb).append(tag).append(" EBC: Created new socket to EdgeBlockServer at ").
                    append(socketIP).append("/").append(socketPort).append(" dbg=").append(dbg).
                    append(" indexBlockMode=").append(indexBlockMode));
            if (terminate && eto.hasSentInterrupt()) {
              terminate = false;
              continue;
            }
            if (terminate) {
              break;
            }
            in = s.getInputStream();
            outsock = s.getOutputStream();
            if (!indexBlockMode) {
              rwq.interrupt();
            }
            forceLoadCheck = true;      // Mark we need to do a force load check after startup
            connected = true;
            break;
          } catch (UnknownHostException e) {
            prta(Util.clear(runsb).append(tag).append(" EBC: Unknown host for socket=").
                    append(socketIP).append("/").append(socketPort));
            if (nextIP == 0) {
              try {
                sleep(300000);
              } catch (InterruptedException e2) {
              }
            }
          } catch (IOException e) {
            if (e.getMessage().contains("Connection refused") || e.getMessage().contains("o route to host") || e.getMessage().contains("timed out")) {
              prta(Util.clear(runsb).append(tag).append(" EBC: ** ").append(e.getMessage()).
                      append(socketIP).append("/").append(socketPort).append(" try again in 30"));
            } else {
              prta(Util.clear(runsb).append(tag).append(" EBC: *** IOException opening client ").
                      append(socketIP).append("/").append(socketPort).append(" try again in 30"));
              e.printStackTrace(getPrintStream());
            }
            if (nextIP == 0) {
              try {
                sleep(30000);
              } catch (InterruptedException e2) {
              }
            }
          }
          if (nextIP == 0) {
            noconnect++;
            if (noconnect % 20 == 5) {
              SendEvent.edgeSMEEvent("EBCNoConnect", "EBC cannot connect to " + socketIP + "/" + socketPort + " from "
                      + Util.getNode() + "/" + Util.getAccount() + " " + Util.getProcess(), this);
            }
          } else {
            try {
              sleep(1000);
            } catch (InterruptedException e) {
            }
          }
          //terminate=false;      // Since we cannot connect, cancel the terminate and reset the ETO.
          eto.resetTimeout();
        }
        if (terminate) {
          break;
        }

        // Send the config data to the server.
        try {
          // TODO: if nextout has a value, we should put it in the configbuf
          
          prt(Util.clear(runsb).append(tag).append(" EBC: write configubuf.len=").append(configbuf.length));
          outsock.write(configbuf);
        } catch (IOException e) {
          if (e.getMessage().contains("Socket closed")) {
            prta(Util.clear(runsb).append(tag).append(" EBC: ** socket closed to ").append(socketIP).append("/").append(socketPort));
          } else {
            prta(Util.clear(runsb).append(tag).append(" EBC: IOException writing config"));
            e.printStackTrace(getPrintStream());
          }
          terminate = true;
        }
        if (!indexBlockMode) {
          if (rwq == null) {
            rwq = new RequestWriter();
          } else if (!rwq.isAlive()) {
            rwq = new RequestWriter();
          }
        }

        // Read the data in EdgeBlock.BUFFER_LENGTH packets and send them to the EdgeBlockQueue
        eto.setIntervals(30000, 300000);   // Set shorter timeout intervals
        while (!terminate) {
          try {
            if (rwq != null) {
              if (!rwq.isAlive()) {
                prta(Util.clear(runsb).append(tag).append(" EBC: RequestWriter has died.  Terminate and reset!"));
                Util.sleep(2000);
                break;          // make a new connection
              }
            }
            // read until the full length is in 
            int l = 0;
            now = System.currentTimeMillis();
            while (l < EdgeBlock.BUFFER_LENGTH) {
              //err=in.read(buf, l, EdgeBlock.BUFFER_LENGTH-l);
              err = Util.socketRead(in, buf, l, EdgeBlock.BUFFER_LENGTH - l);
              //prta("end read err="+err);
              if (terminate) {
                break;
              }
              if (err == 0) {
                prta(Util.clear(runsb).append(tag).append(" EBC: EOF found for socket"));
                connected = false;
                eof = true;
                if (!s.isClosed()) {
                  try {
                    s.close();
                  } catch (IOException e2) {
                  }
                  break;
                }
                break;
              } else {
                l += err;
              }
            }
            if (err == 0) {
              break;       // end of file, exit the read loop to reestablish
            }            // put the block in the queue
            if (terminate) {
              break;
            }
            readMS = System.currentTimeMillis() - now;
            if (readMS > maxReadMS ) maxReadMS = readMS;
            if(readMS > 3000) {
              prta(Util.clear(runsb).append(tag).append("EBC: *** long readMS=").append(readMS).append(" nblks=").append(nblks));
            }
            // If this is a non-julian (index file update), then mark its node down.
            //if(dbg) prta(tag+" node="+node+" indexMode="+indexBlockMode);
            eto.resetTimeout();

            // mark the node the first block received.
            if (node == null && !indexBlockMode) {
              //eb = new EdgeBlock();
              eb.set(buf, 1);
              node = eb.getNode().trim();
              clients.put(node.trim(), this);
              prta(Util.clear(runsb).append(tag).append(" EBC: Mark this thread as node=").append(node));
            }
            if (indexBlockMode) {
              //eb = new EdgeBlock();
              //eb.set(buf,l);
              indexBlockModeBlocks.add(ebp.get(buf, l));   // collect it
              //if(dbg) prta(tag+" indexBlocknode eb="+eb);
            } else {
              //if(dbg) prta(tag+" EBC: queue block nblks="+nblks);
              EdgeBlockQueue.queue(buf);          // put the data in the EdgeBlockQueue - replicator will read from here.
              eb.set(buf, l);

              if (eb.getSeedNameSB().indexOf("INDEXBLOCK") >= 0 || eb.getSeedNameSB().indexOf("CONTROLBLK") >= 0
                      || eb.getSeedNameSB().indexOf("MASTERBLOCK") >= 0 || eb.getSeedNameSB().indexOf("HEARTBEAT!!!") >= 0) {
                if (dbg) {
                  prta(" got a special packet " + eb.toStringBuilder(null));
                }
                continue;
              }
              eb.getData(msbuf);      // Get the MiniSeed buffer
              if (msbuf[0] == 0 && msbuf[18] == 0 && msbuf[8] == 0) {
                continue;    // block of zeros!
              }
              Channel c = EdgeChannelServer.getChannel(MiniSeed.crackSeedname(msbuf, msSeedname));
              //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
              if (c == null) {
                prta(Util.clear(tmpsb).append(tag).append("EBC: ***** new channel found=").append(msSeedname));
                SendEvent.edgeSMEEvent("ChanNotFnd", "EBC: MiniSEED Channel not found=" + msSeedname + " new?", this);
                try {
                  EdgeChannelServer.createNewChannel(msSeedname, MiniSeed.crackRate(msbuf), this);
                } catch (IllegalSeednameException e) {
                  prta(Util.clear(tmpsb).append(tag).append("EBC: illegal seedname creating new channel for ").append(msSeedname));
                }
              }
              // If we are doing channel senders, then maybe we also could do hydra - 
              if (csend != null) {

                try {
                  MiniSeed.crackTime(msbuf, time);
                  millis = time[0] * 3600000 + time[1]
                          * 60000 + time[2] * 1000 + (time[3] + 5) / 10;
                  /*if(ms == null) ms = new MiniSeed(msbuf);
                  else ms.load(msbuf);
                  if(eb.getJulian() != ms.getJulian() || millis != ms.getTimeInMillis() % 86400000L ||
                          !eb.getSeedNameString().equals(MiniSeed.crackSeedname(msbuf)) ||
                          ms.getRate() != MiniSeed.crackRate(msbuf) ||
                          ms.getNsamp() != MiniSeed.crackNsamp(msbuf)) 
                    prta(tag+" bad csend ="+eb.getJulian()+"/"+ms.getJulian()+" mills="+millis+"/"+ms.getTimeInMillis()%86400000L+
                            " "+eb.getSeedNameString()+"/"+MiniSeed.crackSeedname(msbuf)+" ns="+MiniSeed.crackNsamp(msbuf)+"/"+ms.getNsamp()+
                            " rt="+ms.getRate()+"/"+MiniSeed.crackRate(msbuf));*/
                  if (dbg) {
                    if (ms == null) {
                      ms = new MiniSeed(msbuf);
                    } else {
                      ms.load(msbuf);
                    }
                    prta(Util.clear(runsb).append(tag).append(" eb=").append(eb).append(" ns=").append(MiniSeed.crackNsamp(msbuf)).
                            append("/").append(ms.getNsamp()).append(" rt=").append(ms.getRate()).append("/").append(MiniSeed.crackRate(msbuf)));
                  }
                  if (hydra) {
                    if (ms == null) {
                      ms = new MiniSeed(msbuf);
                    } else {
                      ms.load(msbuf);
                    }
                    Hydra.sendNoChannelInfrastructure(ms);            // Send with no channel Infra
                  }

                  csend.send(eb.getJulian(), millis, eb.getSeedNameSB(), MiniSeed.crackNsamp(msbuf), MiniSeed.crackRate(msbuf), l);
                } catch (IllegalSeednameException e) {
                  if (eb.getSeedNameSB().indexOf("INDEXBLOCK") < 0 && eb.getSeedNameSB().indexOf("CONTROLBLK") < 0
                          && eb.getSeedNameSB().indexOf("MASTERBLOCK") < 0 && eb.getSeedNameSB().indexOf("HEARTBEAT!!!") < 0) {
                    prta(Util.clear(runsb).append(tag).append(" Illegal Seedname=").append(eb.getSeedNameSB()));
                  }
                }
              }
            }   // put it in the buffer

            nblks++;
            // if the replicator has few free blocks, slow down reads
            if (nblks % 50 == 0) {
              if (EdgeBlockQueue.getMaxQueue() / 5 > Replicator.free) {
                try {
                  sleep(100);
                  nsleep++;
                  if (nsleep % 100 == 0) {
                    prta(Util.clear(runsb).append(tag).append(" EBC: * sleep, replicator is behind rep.free=").
                            append(Replicator.free).append(" #sleep=").append(nsleep));
                  }
                } catch (InterruptedException expected) {
                }
              }
              now = System.currentTimeMillis();
            }
            if (nblks % 50000 == 0 || (now - lastStatusRun > 600000)) {
              double sec = (now - lastStatusRun) / 1000.;
              prta(Util.clear(runsb).append(tag).append(" EBC: status nblks=").append(nblks/1000).
                      append("k sec=").append(Util.df21(sec)).
                      append(" maxrd=").append(maxReadMS).append("ms nsleep=").append(nsleep).append("/").append(nroll).
                      append(" blk/s=").append(Util.df21((nblks - lastBlocks) / Math.max(0.1, sec))).
                      append(" rep.free=").append(Replicator.free).append(" ").append(ebp.toStringBuilder(null)));
              lastStatusRun = now;
              lastBlocks = nblks;
              if(maxReadMS > maxMaxReadMS) maxMaxReadMS = maxReadMS;
              maxReadMS=0;
              // If we just connected recently, and its today we should FORCELOADIT to make sure the index is complete at startup.
              if (forceLoadCheck) {
                today.setTimeInMillis(now);
                int todayJulian = SeedUtil.toJulian(today);
                int jul = eb.getJulian();
                if (jul == todayJulian) {
                  String nd = eb.getNode();
                  prta(tag + " just connected.  Do FORCELOADIT on " + nd + " nblk=" + nblks + 
                          " jul=" + julian + " " + SeedUtil.fileStub(jul) + "_" + nd);
                  Arrays.fill(msbuf, (byte) 0);
                  EdgeBlockQueue.queue(jul, nd, FORCELOADIT, -1, -1, -1, msbuf, 0);
                  forceLoadCheck = false;
                }
              }
            }
            // If we are starting up using roll back, throttle our reads a bit during the flood
            if (rollback) {
              if (nblks < EdgeBlockQueue.getMaxQueue() / 5 && nblks % 50 == 0) {
                try {
                  sleep(50);
                  nroll++;
                  if(nroll % 100 == 0) {
                    prta(Util.clear(runsb).append(tag).append(" EBC: * sleep, rollback nroll=").append(nroll));
                  }                
                } catch (InterruptedException e) {
                }
              }
            }
          } catch (IOException e) {
            if (s.isClosed() && indexBlockMode) {
              prta(Util.clear(runsb).append(tag).append(" EBC: IOError and s is closed.  blks rcved=").
                      append(indexBlockModeBlocks.size()));
            } else if (e.getMessage().contains("Operation interrupted")) {
              prta(Util.clear(runsb).append(tag).append(" EBC: ETO: ** has sent interrupt, shutdown socket:").append(e.getMessage()));
            } else if (e.toString().indexOf("Socket closed") > 0) {
              prta(Util.clear(runsb).append(tag).append(" EBC: Socket closed.  reopen"));
            } else {
              prta(Util.clear(runsb).append(tag).append(" EBC: IOException reading data from EdgeBlockServer"));
              e.printStackTrace(getPrintStream());
            }
            if (!s.isClosed()) {
              try {
                s.close();
              } catch (IOException e2) {
              }
            }
          } catch (RuntimeException e) {
            prta(Util.clear(runsb).append(tag).append(" nblks=").append(nblks).append(" Got Runtime e=").append(e));
            e.printStackTrace(getPrintStream());
          }
          //prta(tag+" EBC:timer");
          if (s.isClosed()) {
            break;       // if its closed, open it again
          }
        }             // end of while(!terminate) on reading data

        // IF the eto went off and set terminate, do not terminate but force the socket closed
        // and go back to reopen loop.  This insures that full exits only occur when the program
        // is shutting down.
        if (!indexBlockMode && eto.hasSentInterrupt()) {
          prta(Util.clear(runsb).append(tag).append(" EBC:  eto has sent interrupt. Close and reestablish connection term=").
                  append(terminate).append(" interupt=").append(eto.hasSentInterrupt()));
          terminate = EdgeMom.isShuttingDown();
        }
        if (!s.isClosed()) {       // close our socket with predjudice (no mercy!)
          try {
            s.close();
          } catch (IOException e2) {
          }
        }
        connected = false;
        try {
          sleep(5000L);
        } catch (InterruptedException e) {
        }

        // If we are in indexBlockMode mode (just getting some index blocks from IndexFileServer)
        // then we need to abort rather than reopen the socket
        if (indexBlockMode) {
          prta(Util.clear(runsb).append(tag).append(" EBC: indexBLockMode transfer complete"));
          break;
        }

      }               // Outside loop that opens a socket
      prta(Util.clear(runsb).append(tag).append(" EBC: outside of open socket loop.  Start shutdown and exit. eof=").append(eof));
      if (!indexBlockMode) {
        rwq.terminate();
      }
      if (indexBlockMode) {
        prta(Util.clear(runsb).append(tag).append(" EBC: EdgeBlockClient in indexBlock mode exiting.  Index read eof=").
                append(eof).append(" size=").append(indexBlockModeBlocks.size()));
      } else {
        prta(Util.clear(runsb).append(tag).append(" EBC: ** EdgeBlockClient terminated TO.int=").
                append(eto.hasSentInterrupt()).append(" TO.destroy=").append(eto.hasSentDestroy()).
                append(" jul=").append(julian).append(" nblks=").append(nblks));
      }
    } catch (RuntimeException e) {
      prta(Util.clear(runsb).append(tag).append(" EBC: RuntimeException in ").
              append(this.getClass().getName()).append(" e=").append(e.getMessage()));
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMemory")) {
          SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode(), this);
          throw e;
        }
      }
      terminate();
      if (!indexBlockMode) {
        rwq.terminate();
      }
    }
    if (!indexBlockMode) {
      prta(Util.clear(runsb).append(tag).append(" EBC: Out of try block.  Do ETO shutdown and exit."));
    }
    eto.shutdown();           // shutdown the timeout thread
    running = false;
    if (csend != null) {
      csend.close();
    }
    if (s != null) {
      if (!s.isClosed()) {       // close our socket with predjudice (no mercy!)
        try {
          s.close();
        } catch (IOException e2) {
        }
      }
    }
    try {
      shutdown.clear();
      Runtime.getRuntime().removeShutdownHook(shutdown);    // This thread is down, remove its shutdown hook
    } catch (Exception e) {
      if (e != null) {
        if (e.getMessage() != null) {
          if (e.getMessage().contains("Shutdown in progress")) {
            prta(Util.clear(runsb).append(tag).append("EBC: shutdown was in progress"));
          }
        }
      } //else e.printStackTrace(this.getPrintStream());
    }
    prta(tag + " EBC:  ** exiting indexBlockMode=" + indexBlockMode);
  }

  /**
   * this inner class write requests down a socket. If there is a pending write it is discarded.
   */
  final class RequestWriter extends Thread {

    byte[] data;
    boolean terminate;
    long lastSuccess;
    StringBuilder tmpsb = new StringBuilder(50);

    @Override
    public String toString() {
      return socketIP + "/" + socketPort;
    }

    public synchronized void terminate() {
      if (terminate) {
        return;
      }
      terminate = true;
      interrupt();
    }

    public RequestWriter() {
      data = new byte[28];
      lastSuccess = System.currentTimeMillis();
      terminate = false;
      start();
    }

    public synchronized boolean sendRequest(byte[] buf) {
      if (!connected) {
        return false;      // quietly refuse to send to unconnected socket
      }
      if (data[0] == 0) {
        System.arraycopy(buf, 0, data, 0, 28);
        interrupt();
        lastSuccess = System.currentTimeMillis();
        return true;
      }
      prta(Util.clear(tmpsb).append(tag).append(" RW:sendRequest busy. last=").append(System.currentTimeMillis() - lastSuccess));
      if (System.currentTimeMillis() - lastSuccess > 600000L) {
        terminate();
      }
      return false;
    }

    @Override
    public void run() {
      while (!terminate) {
        if (data[0] == 0) {
          try {
            sleep(10);
          } catch (InterruptedException e) {
          }
        } else {
          try {
            outsock.write(data);
          } catch (IOException e) {
            if (s.isClosed()) {
              terminate = true;
              continue;
            }
          }
          data[0] = 0;
        }
      }
      prta(Util.clear(tmpsb).append(tag).append(" RequestWriter thread terminated."));
    }
  }       // end of clase RequestWriter

  /**
   * This class handles the shutdown chores for a shutdown hook
   */
  class ShutdownEdgeBlockClient extends Thread {

    EdgeBlockClient thr;

    public ShutdownEdgeBlockClient(EdgeBlockClient t) {
      thr = t;
    }

    public void clear() {
      thr = null;
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      //try {
      if (dbg) {
        prta(tag + " EBC: Shutdown EdgeBlockCLient=" + thr + " " + toString());
      }
      thr.terminate();
    }
  }

  /**
   * ForceCheckServer.java - This server looks for a telnet type connection,accepts a message of the
   * form yyyy,doy,node\n and forms up a FORCELOADIT! block for the EdgeBlockQueue. This triggers
   * the opening of the file in Replicator and causes the file to be checked.
   *
   *
   * @author davidketchum
   */
  public final class ForceCheckServer extends Thread {

    int port;
    ServerSocket d;
    int totmsgs;
    boolean terminate;
    boolean running;

    public boolean isRunning() {
      return running;
    }

    public void terminate() {
      terminate = true;
      interrupt();
      try {
        d.close();
      } catch (IOException e) {
      }
    }   // cause the termination to begin

    public int getNumberOfMessages() {
      return totmsgs;
    }

    /**
     * Creates a new instance of ForceCheckServers
     *
     * @param porti The port that will be used for this service normally 7960
     * AnssPorts.EDGEMOM_FORCE_CHECK_PORT
     */
    public ForceCheckServer(int porti) {
      port = porti;
      terminate = false;
      // Register our shutdown thread with the Runtime system.
      Runtime.getRuntime().addShutdownHook(new ShutdownForceCheck(this));
      running = true;
      gov.usgs.anss.util.Util.prta("new Thread " + getName() + " " + getClass().getSimpleName());
      start();
    }

    @Override
    public String toString() {
      return "FChk: " + port + " d=" + d + " port=" + port + " totmsgs=" + totmsgs;
    }

    @Override
    public void run() {
      boolean dbg = false;
      long now;
      StringBuilder sb = new StringBuilder(10000);
      byte[] bf = new byte[512];
      // OPen up a port to listen for new connections.
      while (true) {
        try {
          //server = s;
          if (terminate) {
            break;
          }
          prta(tag + " FChk: " + port + " Open Port=" + port);
          d = new ServerSocket(port);
          break;
        } catch (SocketException e) {
          if (e.getMessage().contains("Address already in use")) {
            prt(tag + " FChk: " + port + " Address in use - exit ");
            return;
          } else {
            prt(tag + " FChk: " + port + " Error opening TCP listen port =" + port + "-" + e.getMessage());
            try {
              Thread.sleep(2000);
            } catch (InterruptedException E) {
            }
          }
        } catch (IOException e) {
          prt(tag + " FChk: " + port + "ERror opening socket server=" + e.getMessage());
          try {
            Thread.sleep(2000);
          } catch (InterruptedException E) {
          }
        }
      }

      while (true) {
        if (terminate) {
          break;
        }
        try {
          prta(tag + " FChk: " + port + " at accept");
          Socket s = d.accept();
          prta(tag + " FChk: " + port + " from " + s);
          try {
            OutputStream out = s.getOutputStream();
            out.write("yyyy,doy,node - example : 2007,331,8#1 (Enter blank line to exit)\n".getBytes());

            try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
              String line;
              while ((line = in.readLine()) != null) {
                if (line.length() < 2) {
                  break;
                }
                // The line should be yyyy,doy,node\n
                String[] parts = line.split(",");
                for (int i = 0; i < parts.length; i++) {
                  prta(i + " " + parts[i]);
                }
                if (parts.length == 3) {
                  int julian = SeedUtil.toJulian(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                  prta(tag + " FChk: " + port + " Do force load on " + parts[0] + " " + parts[1] + " " + parts[2] + " jul=" + julian);
                  EdgeBlockQueue.queue(julian, parts[2], FORCELOADIT, -1, -1, -1, bf, 0);
                } else {
                  out.write("Line must be of the form YYYY,DOY,NODE (2008,20,8#1)\n".getBytes());
                }
              }
            }
          } catch (IOException e) {
            Util.SocketIOErrorPrint(e, "FChk:" + port + " IOError on socket");
          } catch (NumberFormatException e) {
            prt(tag + " FChk:" + port + " RuntimeException in EBC FChk e=" + e + " " + (e == null ? "" : e.getMessage()));
            if (e != null) {
              e.printStackTrace();
            }
          }
          prta(tag + " FChk:" + port + " ForceCheckHandler has exit on s=" + s);
          if (s != null) {
            if (!s.isClosed()) {
              try {
                s.close();
              } catch (IOException e) {
                prta(tag + " FChk:" + port + " IOError closing socket");
              }
            }
          }
        } catch (IOException e) {
          if (!EdgeMom.isShuttingDown() && !terminate) {
            Util.SocketIOErrorPrint(e, tag + " FChk: accept loop port=" + port);
          } else {
            break;
          }
          if (e.getMessage().contains("operation interrupt")) {
            prt(tag + " FChk:" + port + " interrupted.  continue terminate=" + terminate);
            continue;
          }
          if (terminate) {
            break;
          }
        }
      }       // end of infinite loop (while(true))
      //prt("Exiting ForceCheckServers run()!! should never happen!****\n");
      prt(tag + " FChk: " + port + " read loop terminated");
      running = false;
    }

    private class ShutdownForceCheck extends Thread {

      ForceCheckServer thr;

      public ShutdownForceCheck(ForceCheckServer t) {
        thr = t;
        gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
      }

      /**
       * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup
       * actions to occur
       */
      @Override
      public void run() {
        thr.terminate();
        prta(tag + " FChk: " + port + "Shutdown Done.");
      }
    }
  }

  /**
   * The unit test connects to a server and in the main loop prints out based on blocks retrieved
   * from the EdgeBlockQueue
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.init();

    IndexFile.init();
    EdgeProperties.init();
    EdgeBlockQueue ebq = new EdgeBlockQueue(10000);
    EdgeBlockQueue.setDebug(true);

    EdgeBlockClient u = new EdgeBlockClient("-h localhost -p 7983 -rb", "EBClient"); // main() test
    u.setDebug(true);
    int nextin = EdgeBlockQueue.getNextSequence();
    Util.prt("EBC: starting sequence=" + nextin + " " + ebq.toString());
    byte[] msbuf = new byte[512];
    for (;;) {
      while (nextin != EdgeBlockQueue.getNextSequence()) {
        EdgeBlock eb = EdgeBlockQueue.getEdgeBlock(nextin);
        if (eb == null) {
          Util.prta("EdgeBlock is null nextin=" + nextin + " not available.");
        } else {
          int julian = eb.getJulian();
          int block = eb.getBlock();
          if (!eb.isContinuation()) {
            eb.getData(msbuf);
            MiniSeed ms;
            try {
              if (block > 0 && eb.getExtentIndex() >= 0) {
                ms = new MiniSeed(msbuf);
                Util.prt("n=" + nextin + " nd=" + eb.getNode() + " j=" + julian
                        + " b=" + Util.leftPad("" + block, 9)
                        + " idxblk/ext=" + Util.leftPad("" + eb.getIndexBlock(), 4) + "/"
                        + Util.leftPad("" + eb.getExtentIndex(), 2) + "." + Util.leftPad("" + (block % 64), 2)
                        + " " + ms.toString());
              } else {
                Util.prt("n=" + nextin + " nd=" + eb.getNode() + " j=" + julian
                        + " b=" + Util.leftPad("" + block, 9)
                        + "  idxblk/ext=" + Util.leftPad("" + eb.getIndexBlock(), 4) + "/"
                        + Util.leftPad("" + eb.getExtentIndex(), 2) + "." + Util.leftPad("" + (block % 64), 2)
                        + " *** not ms blk");
              }
            } catch (IllegalSeednameException e) {
              Util.prta("Illegal seedname getting buffer nextin=" + nextin);
            }
          } else {
            Util.prt("n=" + nextin + " nd=" + eb.getNode() + " j=" + julian
                    + " bk=" + Util.leftPad("" + block, 9)
                    + " idxblk/ext=" + Util.leftPad("" + eb.getIndexBlock(), 4) + "/"
                    + Util.leftPad("" + eb.getExtentIndex(), 2) + "." + Util.leftPad("" + (block % 64), 2)
                    + " *** continuation");
          }
        }
        nextin++;
      }
    }
  }

}
