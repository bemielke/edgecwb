/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;
import gnu.trove.map.hash.TLongObjectHashMap;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.EdgeThreadTimeout;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.EdgeBlock;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.EdgeFileDuplicateCreationException;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeFileReadOnlyException;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 *
 * This thread is created by the EdgeBlockServer and
 * serves EdgeBlock replication data to one client. It takes data from the EdgeBlockQueue and forwards
 * it to the client. It has a timeout to allow it to exit if the socket goes
 * horribly wrong. This Thread is never created via the EdgeMom configuration
 * file.
 * <pre>
 * This EdgeThread uses the command line from the EdgeBlockServer that creates it.  Only the
 * -dbg option is actually used by this class.
 * -cwbcwb set if replication is coming from another CWB and not an EdgeNode (so node is ignored in requests)
 * </pre> Created on February, 2006, 3:12 PM
 *
 * @author davidketchum
 */
public final class EdgeBlockSocket extends EdgeThread {

  private static final StringBuilder HEARTBEAT = new StringBuilder(12).append("HEARTBEAT!!!");
  private static final StringBuilder REQUESTEDBLK = new StringBuilder(12).append("REQUESTEDBLK");

  private Socket s;               // Socket we will read forever (or misalignment)
  private boolean dbg;
  private long bytesin;
  private long bytesout;
  private long lastWrite;             // time of last write for possible time out
  private long noob;                  // number of requested blocks sent
  private int nextout;                // sequence of next one to go out
  private boolean cwbcwb;             // This is a cwb EdgeBlockServer respond to all node requests
  private String fromIP;
  private OutputStream outsock;
  private InputStream insock;             // for getting tag and config data, and later requests 
  private String orgtag;
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder runsb = new StringBuilder(100);

  /**
   * terminate this thread - shuts down the socket as well.
   */
  @Override
  public void terminate() {
    terminate = true;
    if (!s.isClosed()) {
      try {
        s.close();
      } catch (IOException expected) {
      }
    }
    this.interrupt();
  }

  public String getFromIP() {
    return fromIP;
  }

  public boolean isClosed() {
    if (s == null) {
      return true;
    } else {
      return s.isClosed();
    }
  }

  /**
   * set terminate variable
   *
   * @param t If true, the run() method will terminate at next change and exit
   */
  public void setTerminate(boolean t) {
    terminate = t;
    if (terminate) {
      terminate();
    }
  }

  /**
   * return a descriptive tag of this socket (basically "EBS:" plus the tag of
   * creation
   *
   * @return A descriptive tag
   */
  @Override
  public String getTag() {
    return tag;
  }
  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  long lastMonBytesOut;

  /**
   * @return Monitor Style StringBuilder.
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    long nb = bytesout - lastMonBytesOut;
    lastMonBytesOut = bytesout;
    return monitorsb.append("EBSk").append(orgtag).append("-BytesOut=").append(nb).append("\n");
  }

  /**
   * return a status string for this type of thread
   *
   * @return A status string with identifier and output measures
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append(getTag()).
            append(" in=").append(Util.rightPad(bytesin, 9)).append(" out=").
            append(Util.rightPad(bytesout, 9)).append(" last=").
            append(Util.rightPad(System.currentTimeMillis() - lastWrite, 9)).append(" noob=").append(noob);
  }

  /**
   * return console output. For this there is none.
   *
   * @return The console output which is always empty.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * set debug flag
   *
   * @param t value to set
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * return InetAdddress of the remote end (usually an RTS)
   *
   * @return The InetAdddress of the remote end.
   */
  public InetAddress getRemoteInetAddress() {
    return s.getInetAddress();
  }

  /**
   * return measure of input volume
   *
   * @return The number of bytes read from this socket since this objects
   * creation.
   */
  @Override
  public long getBytesIn() {
    return bytesin;
  }

  /**
   * return measure of output volume
   *
   * @return The number of bytes written to this socket since this object was
   * created
   */
  @Override
  public long getBytesOut() {
    return bytesout;
  }

  /**
   * return ms of last IO operation on this channel (from
   * System.currentTimeMillis()
   *
   * @return ms value at last read or I/O operation
   */
  public long getlastWrite() {
    return lastWrite;
  }

  /**
   * Creates a new instance of EdgeBlockSocket to send data to one client. This
   * is normally created by EdgeBlockSocket so it must allow all of its argments
   * as well as its own. right now on -dbg does anything
   *
   * @param argline The command line passed through from the EdgeBlockServer
   * @param tag The tag for output for this object.
   * @param ss The socket to use for this object
   * @param parent EdgeThread to use for logging (normally the EdgeBlockSocket)
   */
  public EdgeBlockSocket(String argline, String tag, Socket ss, EdgeThread parent) {
    super(argline, tag);
    s = ss;
    this.setEdgeThreadParent(parent);
    fromIP = s.getInetAddress().toString();
    orgtag = tag;
    if (orgtag.indexOf("/") > 0) {
      orgtag = orgtag.substring(0, orgtag.indexOf("/"));
    }
    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].substring(0, 1).equals(">")) {
        break;
      }       
      switch (args[i]) {
        case "-p":
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-dbgebq": // These switches are parsed in the EdgeBlockServer - not here
        case "-dbgibc":
        case "-poff":
          break;
        case "-cwbcwb":
          cwbcwb = true;
          break;
        default:
          prt(Util.clear(tmpsb).append("EdgeBlockSocket unknown switch=").append(args[i]).append(" ln=").append(argline));
          break;
      }
    }
    this.tag = "EBS:" + tag;
    terminate = false;
    running = false;
    lastWrite = System.currentTimeMillis();
    prta(Util.clear(runsb).append("Start EdgeBlockSocket ss=").append(ss).append(" dbg=").append(dbg).
           append(" cwbcwb=").append(cwbcwb));
    nextout = -1;
    start();
  }

  private static int readFully(InputStream insock, byte[] buf, int len) throws IOException {
    int l = 0;
    int got;
    while (l < len) {
      try {
        while (insock.available() <= 0) {
          Util.sleep(10);
        }
        got = insock.read(buf, l, len - l);
      } catch (InterruptedIOException e) {
        got = e.bytesTransferred;
      }
      if (got >= 0) {
        l += got;
      } else if (got < 0) {
        return -1;
      }
    }
    return l;
  }

  /**
   * This monitors for new data in the EdgeBlockQueue and sends that data along
   * to the client end
   */
  @Override
  public void run() {
    running = true;
    outsock = null;
    insock = null;    // for getting tag and config data
    byte[] configbuf = new byte[512];
    byte[] ebuf = null;
    MiniSeed ms = null;

    try {
      outsock = s.getOutputStream();
      insock = s.getInputStream();
    } catch (IOException e) {
      prta(Util.clear(runsb).append(getTag()).append("EBS: IOException getting output stream"));
      e.printStackTrace(getPrintStream());
    }
    long lastStatus = System.currentTimeMillis();
    long lastHeartBeat = System.currentTimeMillis();
    long totalWriteMS = 0;
    // Set up a timeout to blow this away if the socket quits working.
    //  0 int next sequence
    //  4 int if not zero, play back is true
    //  8 char*10 10 character "tag" for this instance.
    EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 1200000, 3000000);
    try {
      prt(Util.clear(runsb).append(getTag()).append(" Start read of configbuf"));
      int err = readFully(insock, configbuf, 22);
      // We expect to get 22 character ints with seq, ints with roll flag, and
      // 10 characters of tag, and node (not used)
      if (err == 22) {
        ByteBuffer bf = ByteBuffer.wrap(configbuf);
        bf.clear();
        nextout = bf.getInt();
        int roll = bf.getInt();
        boolean playback = roll != 0;
        byte[] tagbuf = new byte[10];
        bf.get(tagbuf, 0, 10);
        byte[] nd = new byte[4];
        bf.get(nd, 0, 4);
        int len = 10;
        for (int i = 0; i < 10; i++) {
          if (tagbuf[i] == 0) {
            len = i;
          }
        }
        String st = new String(tagbuf).substring(0, len).trim();
        tag = tag + st;
        for (int i = 0; i < 22; i++) {
          configbuf[i] = 0;
        }
        // if the user has not requested a sequence (nextout <= 0), then use rollback
        // to decide if we are going to play back a lot of data or just start picking up
        prt(Util.clear(runsb).append(getTag()).append(" nextout=").append(nextout).
                append(" playback=").append(playback).append(" getLapped=").append(EdgeBlockQueue.getLapped()));
        if (nextout <= 0) {                // need to pick a starting point
          if (playback) {
            if (EdgeBlockQueue.getLapped())  {// Do 90 % of the buffer if it has lapped
              nextout = EdgeBlockQueue.getNextSequence() - EdgeBlockQueue.getMaxQueue() * 9 / 10;
            } else {
              nextout = 1;               // if not just start at beginning
            }
          } else {
            nextout = EdgeBlockQueue.getNextSequence();// No rollback, start with next
          }
        }
        prta(Util.clear(runsb).append("EBS: tag set to ").append(tag).append(" playback=").append(playback).
                append(" nextout=").append(nextout).append(" que=").append(EdgeBlockQueue.getNextSequence()).
                append(" lapped=").append(EdgeBlockQueue.getLapped()));
      } else {
        prta(Util.clear(runsb).append(getTag()).append(" EBS: *** getting config did not return 22=").
                append(err).append(" close connection"));
        terminate = true;
      }
    } catch (IOException e) {
      Util.SocketIOErrorPrint(e, getTag() + " *** trying to read configuration", getPrintStream());
    }

    // Run until terminate flag is set
    eto.setIntervals(1200000, 3000000);
    eto.resetTimeout();
    EdgeBlockRequest ebrqst = new EdgeBlockRequest();
    int behind = 0;
    long nout = 0;
    long lastline = System.currentTimeMillis();
    long lastnout = 0;
    long lastTotalWriteMS= 0;
    while (!terminate) {
      while (s.isClosed()) {       // socket is closed - time to bail
        try {
          sleep(1000L);
        } catch (InterruptedException expected) {
        }
        terminate();
        break;
      }
      if (terminate) {
        break;
      }
      // For each block in queue
      while (nextout != EdgeBlockQueue.getNextSequence()) {
        if (terminate) {
          break;
        }

        if (Math.abs(EdgeBlockQueue.getNextSequence() - nextout) > EdgeBlockQueue.getMaxQueue()) {
          behind++;
          prta(Util.clear(runsb).append(getTag()).append(" ****** EBS: I am way behind!!! reset nextout=").append(nextout).
                  append("/").append(EdgeBlockQueue.getNextSequence()).append("/").
                  append(EdgeBlockQueue.getMaxQueue()).append(" to 95%"));
          nextout = EdgeBlockQueue.getNextSequence() - EdgeBlockQueue.getMaxQueue() * 19 / 20;  // reset to 5% of queue
          if (nextout < 0) {
            nextout += EdgeBlockQueue.MAX_SEQUENCE;
          }
        }
        try {
          byte[] b = EdgeBlockQueue.get(nextout);    // DEBUG: detect zeros going out!
          eto.resetTimeout();
          if (b[36] == 0 && b[37] == 0 && b[38] == 0 && b[39] == 0) {
            prta(Util.clear(runsb).append(getTag()).append(" write zeros block nextout=").append(nextout));
          }
          // write this block out
          long now = System.currentTimeMillis();        
          outsock.write(EdgeBlockQueue.get(nextout));
          totalWriteMS += System.currentTimeMillis() - now;// measure total time to write blocks
          bytesout += EdgeBlock.BUFFER_LENGTH;

          // extra logging if requested.
          if (dbg && b[36] != 0 && b[37] != 0 && b[38] != 0 && b[39] != 0) {
            EdgeBlock eb = EdgeBlockQueue.getEdgeBlock(nextout);
            if (ms == null) {
              ebuf = new byte[512];
              eb.getData(ebuf);
              try {
                ms = new MiniSeed(ebuf);
              } catch (IllegalSeednameException expected) {
              }

            }
            eb.getData(ebuf);
            if (ms != null) {
              try {
                ms.load(ebuf, 0, 512);
              } catch (IllegalSeednameException e) {
                prta("nextout=" + nextout + " e=" + e);
              }
            }
            prta(Util.clear(runsb).append(getTag()).append(" next=").append(EdgeBlockQueue.getNextSequence()).
                    append(" nextout=").append(nextout) + " ms=" + ms);
          }
          nextout = EdgeBlockQueue.incSequence(nextout);  // set to next sequence

          lastWrite = System.currentTimeMillis();
          lastHeartBeat = lastWrite;                  // reset heartbeat counter
          if (nextout % 50000 == 0 || lastWrite - lastline > 600000) {
            now = lastWrite;
            long kBps = (nout - lastnout) * 520 * 8 / (now - lastline);
            long pct = (EdgeBlockQueue.getNextSequence() - nextout) * 100L / EdgeBlockQueue.getMaxQueue();
            prta(Util.clear(runsb).append(getTag()).append(" nextout=").append(nextout).
                    append(" ebq.next=").append(EdgeBlockQueue.getNextSequence()).
                    append(" kbps=").append(kBps).
                    append(" ").append(pct).
                    append("% used wrMS=").append(totalWriteMS - lastTotalWriteMS).
                    append(" ebq.max=").append(EdgeBlockQueue.getMaxQueue()).
                    append(" #out=").append(nout - lastnout).append(" #oob=").append(noob));
            lastTotalWriteMS = totalWriteMS;
            lastline = now;
            lastnout = nout;
            if (pct > 25) {
              SendEvent.edgeSMEEvent("EBSPctUsed", tag + " EBServer " + pct + "% used", this);
            }
          }
          nout++;
        } catch (IOException e) {
          Util.SocketIOErrorPrint(e, getTag() + " writing to socket", getPrintStream());
          terminate = true;
          break;
        } catch (RuntimeException e) {
          prta(Util.clear(runsb).append("Got an Runtime exception e=").append(e));
          e.printStackTrace(getPrintStream());
        }
      }
      // If nothing is happening, check for need to send a heartbeat.
      if (System.currentTimeMillis() - lastHeartBeat > 15000) {
        prta(Util.clear(runsb).append(getTag()).append(" Nothing going on send heartbeat nextout=").append(nextout).
                append("/").append(EdgeBlockQueue.getNextSequence()));
        EdgeBlockQueue.queue(2446801, IndexFile.getNode(), HEARTBEAT, -1, -1, -1, configbuf, 0);
        lastHeartBeat = System.currentTimeMillis();
      }
      if (terminate) {
        break;
      }

      // Now see if any buffers are in the request queue
      if (ebrqst.getNext() > 0) {
        int iblk = 0;
        int start = ebrqst.getEdgeBlock(0).getBlock();
        while (ebrqst.getNext() != iblk) {
          if (terminate) {
            break;
          }
          try {
            //prta("Write OOB block "+iblk+"="+ebrqst.getEdgeBlock(iblk).toString());
            outsock.write(ebrqst.getEdgeBlock(iblk).get());
            eto.resetTimeout();
            lastWrite = System.currentTimeMillis();
          } catch (IOException e) {
            Util.SocketIOErrorPrint(e, getTag() + "writing to request", getPrintStream());
            terminate = true;
            break;
          }
          iblk++;
          noob++;
        }
        prta(Util.clear(runsb).append(getTag()).append(" rqst done ").append(iblk).append(" oob=").append(noob).
                append(" st=").append(start));
        ebrqst.reset();
      }
      // try a short sleep and try again
      try {
        sleep(20L);
      } catch (InterruptedException e) {
        if (eto.hasSentInterrupt()) {
          prta(Util.clear(runsb).append(getTag()).append(" *** ETO: has interrupted. terminate! ").
                  append(s.toString()));
          terminate = true;
        }
      }

      // check on periodic stuff
      if ((System.currentTimeMillis() - lastStatus) > 1800000) {
        prta(Util.clear(runsb).append(getTag()).append("Status:  nextout=").append(EdgeBlockQueue.getNextSequence()).
                append("/").append(EdgeBlockQueue.getMaxQueue()).append(" #out=").append(nout).append(" #oob=").append(noob));
        IndexFileReplicator.closeStale(43200010);
        lastStatus = System.currentTimeMillis();
      }

    }         // End of while(!terminate)  

    // We have been asked to terminate, close up the socket and cause children UTLs to close
    prta(Util.clear(runsb).append(getTag()).append(" EBS: *** leaving run() loop.  Terminate EdgeBlockSocket. TO: int=").
            append(eto.hasSentInterrupt()).append(" destroy=").append(eto.hasSentDestroy()));
    ebrqst.terminate();
    eto.shutdown();         // Shutdown the TimeOut thread
    if (!s.isClosed()) {
      try {
        s.close();
      } catch (IOException expected) {
      }
    }
    running = false;
    terminate = false;
    prta(Util.clear(runsb).append(getTag()).append(" EBS: has Terminated nextout=").append(nextout).
            append(" que=").append(EdgeBlockQueue.getNextSequence()));
  }

  /**
   * this internal class listens on the input of a paired EdgeBlockSocket for
   * request for blocks from the client. The requests are read, and the file
   * opened and a queue of additional blocks built which would be sent out on
   * the usual pipe. The user of this would look for data in the queue by
   * calling getNext() and if it is non-zero processing all of the data then
   * calling reset() to indicate it is done. requests are of the form : "RQST" 4
   * character alignment string int julian string node(4) int startBlock int
   * endBlock
   */
  final class EdgeBlockRequest extends Thread {

    int REQUEST_LENGTH = 28;
    EdgeBlock[] eb;
    boolean terminate;
    int next;       // How many blocks are in eb
    String thisNode;
    StringBuilder tmpsb = new StringBuilder(20);

    EdgeBlockRequest() {

      eb = new EdgeBlock[64];
      for (int i = 0; i < 64; i++) {
        eb[i] = new EdgeBlock();
      }
      next = 0;
      thisNode = (IndexFile.getNode() + "    ").substring(0, 4);
      start();
    }

    /**
     * getEdgeBlock() get the given edgeblock
     *
     * @param i The edge block to get (0 to 63 please!)
     * @return The edgeblock in the ith position
     */
    public synchronized EdgeBlock getEdgeBlock(int i) {
      return eb[i];
    }

    /**
     * return the next place we would put data this varies between zero (empty)
     * at 65(full)
     *
     * @return The next one we would use, varies between zero (empty) and 65
     * (full)
     */
    public synchronized int getNext() {
      return next;
    }  // get number of blocks in eb

    /**
     * the user calls this to indicate it has emptied the blocks in the queue
     */
    public void reset() {
      next = 0;
    }

    @Override
    public void run() {
      byte[] buf = new byte[REQUEST_LENGTH];
      byte[] data = new byte[64 * 512];
      TLongObjectHashMap<Long> recent = new TLongObjectHashMap<>();
      //TreeMap<String, Long> recent = new TreeMap<String, Long>();
      ByteBuffer bb = ByteBuffer.wrap(buf);
      StringBuilder node = new StringBuilder(4);
      StringBuilder align = new StringBuilder(4);
      int julian;
      int startBlock;
      int endBlock;
      int indexBlock;
      int extentIndex;
      IndexFileReplicator idx;
      prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: rqst started for ").append(s.toString()));
      while (!terminate) {
        try {
          int len = readFully(insock, buf, REQUEST_LENGTH);

          // Is this not a normal length request? If so, check on a possible shutdown.
          if (len != REQUEST_LENGTH) {
            if (len == -1) {
              prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: read at EOF len=").append(len).
                      append(" sclosed=").append(s.isClosed()));
              terminate = true;
              if (!s.isClosed()) {
                try {
                  s.close();
                } catch (IOException expected) {
                }
              }
              continue;
            }
            bytesin += REQUEST_LENGTH;
            prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: read did not return fully.  How can this be!"));
            // Did not get all of the data, wait for it
            try {
              sleep(1000);
            } catch (InterruptedException expected) {
            }
            continue;
          }
        } catch (IOException e) {
          Util.SocketIOErrorPrint(e, getTag() + "EBRQST: readin rqst info from " + s.toString(), getPrintStream());
          terminate = true;
          terminate();
        }
        bb.clear();
        Util.clear(align);
        for (int i = 0; i < 4; i++) {
          align.append((char) buf[i]);
        }
        if (Util.stringBuilderEqual(align, "RQST")) {
          bb.position(4);
          julian = bb.getInt();
          Util.clear(node);
          for (int i = 0; i < 4; i++) {
            node.append((char) buf[8 + i]);
          }
          //node = new String(buf,8,4);
          bb.position(12);
          startBlock = bb.getInt();
          endBlock = bb.getInt();
          indexBlock = bb.getInt();
          extentIndex = bb.getInt();
          if (!Util.stringBuilderEqual(node, thisNode) && !cwbcwb) {
            //prta("Rquest not for this node "+node.trim()+"!="+thisNode+"| "+SeedUtil.fileStub(julian)+" blk="+startBlock+"-"+endBlock+" index="+indexBlock);
            continue;
          }
          //prta("EBS: rqst for "+julian+"_"+node.trim()+" "+startBlock+"-"+endBlock+
          //    " idx="+indexBlock+" ext="+extentIndex);
          idx = IndexFileReplicator.getIndexFileReplicator(julian, node);
          Long last;

          // We may get idx=null because file does not exist on this node; do not check it too often
          // since broadcast of request may be for another node and file will never be here.
          if (idx == null) {
            try {
              last = recent.get(IndexFileReplicator.getHash(node, julian));
              if (last != null) {
                if (System.currentTimeMillis() - last < 120000) {
                  //prta("EBS: rqst too soon "+julian+"_"+node);
                  continue;
                }
              }
              // Check to see if this file is on this server. If not, add it to the recent bad list and continue
              String name = SeedUtil.fileStub(julian) + "_" + node.toString().trim() + ".ms";
              if (IndexFileReplicator.fileExists(name) == null) {
                recent.put(IndexFileReplicator.getHash(node, julian), System.currentTimeMillis());
                prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: rqst file not on server2 jul=").
                        append(julian).append("_").append(node));
                continue;
              }
              try {
                prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: rqst file is not open open ").
                        append(SeedUtil.fileStub(julian)).append(" node=").append(Util.toAllPrintable(node)).append(" 0 "));
                idx = new IndexFileReplicator(julian, node.toString().trim(), 0, false, false);
              } catch (EdgeFileDuplicateCreationException e) {
                prta(Util.clear(tmpsb).append(getTag()).append("***** IFR: rare duplicate creation found.  Handle! e=").append(e));
                idx = IndexFileReplicator.getIndexFileReplicator(julian, node);
                if (idx == null) {
                  Util.prt("This is impossible - no index file rep found for duplicate creation!");
                  System.exit(0);
                }
              }
            } catch (FileNotFoundException e) {
              recent.put(IndexFileReplicator.getHash(node, julian), System.currentTimeMillis());
              prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: *** rqst file not on server jul=").
                      append(julian).append("_").append(node));
              continue;
            } catch (IOException e) {
              prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: *** error rqst opening IFR for file jul=").
                      append(julian).append(" node=").append(node));
              e.printStackTrace(getPrintStream());
              continue;
            } catch (EdgeFileReadOnlyException e) {
              prt(Util.clear(tmpsb).append(getTag()).append("EBRQST: *** Impossible rqst edgefile read only when opening IFR jul=").
                      append(julian).append(" node=").append(node).append(e.getMessage()));
            }
          }

          // read in the blocks and put them in the queue
          if (terminate) {
            continue;
          }
          if (idx == null) {
            continue;
          }
          try {
            prta(Util.clear(tmpsb).append(getTag()).append(" EBRQST: rqst ").
                    append(SeedUtil.fileStub(julian)).append("_").append(node).
                    append(" blk=").append(startBlock).append(" #blk=").append(endBlock - startBlock + 1));
            int readBlock = idx.getDataRawDisk().readBlock(data, startBlock, (endBlock - startBlock + 1) * 512);
            synchronized (this) {
              for (int iblk = 0; iblk <= (endBlock - startBlock); iblk++) {
                // REQUESTEDBLK is instead of a seedname.  If not a seed hdr, send -julian
                boolean notSeed = false;
                for (int i = iblk * 512; i < iblk * 512 + 6; i++) {
                  if (!Character.isDigit(data[i])) {
                    //prt("seq not digit at i="+i+" "+data[i]);
                    notSeed = true;
                    break;
                  }
                }
                if (!notSeed) {
                  if (data[iblk * 512 + 6] != 'D' || data[iblk * 512 + 7] != ' ') {
                    //prt("Not 'D ' "+data[iblk*512+6]+" "+data[iblk*512+7]);
                    notSeed = true;
                  }
                }
                if (!notSeed) {
                  for (int i = iblk * 512 + 8; i < iblk * 512 + 20; i++) {
                    if (!Character.isLetterOrDigit(data[i]) && data[i] != ' ') {
                      //prt("not letter or digit or space at i="+i+" "+data[i]);
                      notSeed = true;
                      break;
                    }
                  }
                }
                boolean zero = true;
                for (int i = iblk * 512; i < iblk * 512 + 10; i++) {
                  if (data[i] != 0) {
                    zero = false;
                    break;
                  }
                }
                if (zero) {
                  prta(Util.clear(tmpsb).append(getTag()).append(" EBRQST : rqst read zero block from ").
                          append(SeedUtil.fileStub(julian)).append("_").append(node).append(" blk=").append(startBlock + iblk));
                  //continue;
                }
                /*if(notSeed) prt("RQST: seed not found iblk="+(startBlock+iblk)+" "+
								 data[iblk*512]+" "+data[iblk*512+1]+" "+data[iblk*512+2]+" "+data[iblk*512+3]+" "+
								 data[iblk*512+4]+" "+data[iblk*512+5]+" "+data[iblk*512+6]+" "+data[iblk*512+7]+" "+
								 data[iblk*512+8]+" "+data[iblk*512+9]+" "+data[iblk*512+10]+" "+data[iblk*512+11]+" "+
								 data[iblk*512+12]+" "+data[iblk*512+13]+" "+data[iblk*512+14]+" "+data[iblk*512+15]+" "+
								 data[iblk*512+16]+" "+data[iblk*512+17]+" "+data[iblk*512+18]+" "+data[iblk*512+10]+" "+
								 data[iblk*512+20]);*/
                //else prt("RQST: seed found iblk="+(startBlock+iblk)+" "+ (new String(data, 512*iblk, 20)));
                eb[next].set((notSeed || zero ? -julian : julian), node, REQUESTEDBLK, startBlock + iblk,
                        indexBlock, extentIndex, data, iblk * 512, iblk);
                next++;
              }
            }

            // Now wait for the output loop to empty us (it will set the next back to zero)
            while (next != 0) {
              try {
                sleep(10);
              } catch (InterruptedException expected) {
              }
            }
          } catch (IOException e) {
            prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: IOException rqst read blk=").
                    append(startBlock).append(" #blk=").append(endBlock - startBlock + 1).append(" of ").
                    append(SeedUtil.fileStub(julian)).append("_").append(node).append(" e=").append(e));
            if (e.toString().contains("EOFExcept")) {
              try {
                prta(Util.clear(tmpsb).append(getTag()).append(" EOF at file.blks=").
                        append(idx.getDataRawDisk().length() / 512L));
              } catch (IOException e2) {
                prta(Util.clear(tmpsb).append("GOT IOException geting length of file! ").append(e2.toString()));
              }
            }
            e.printStackTrace(getPrintStream());
          }
        } else {
          prta(Util.clear(tmpsb).append(getTag()).append("EBRQST: Request return invalid lead in.  Close socket"));
          terminate = true;
          terminate();
        }
      }     // end of !terminate loop
      prta(getTag() + "EBRQST: rqst is terminated.");
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }
  }
}
