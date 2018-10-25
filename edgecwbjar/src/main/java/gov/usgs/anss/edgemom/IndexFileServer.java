/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edge.EdgeBlock;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.TimeoutThread;
import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.ByteBuffer;
import gov.usgs.anss.util.*;

/**
 * This class listens to connections, read in information on a target file
 * (julian day, node) and causes that entire IndexFile, if present, to be put
 * into the EdgeBlockQueue for this instance. The IndexFile is therefore forced
 * in its entirety to any listening EdgeBlockClients/Replicators. This class
 * should be configured whenever an EdgeBlockServer is configured.
 *
 * <BR>
 * <PRE>
 *switch args    Description
 *-p     pppp    The port to listen on
 *-dbg           Turn on debug output
 *>[>]   filename Redirect output to this file
 * </PRE>
 *
 * IndexFileServer.java
 *
 * @author ketchum
 */
public final class IndexFileServer extends EdgeThread {

  private static final StringBuilder INDEXFILESRV = new StringBuilder(12).append("INDEXFILESRV");
  private ArrayList<IndexServerThread> thr = new ArrayList<IndexServerThread>(10);
  private ServerSocket ss;                // the socket we are "listening" for new connections
  //private GregorianCalendar lastStatus;
  private long nblksOut;
  private int nconnects;
  private int port;
  private boolean dbg;
  private final Integer mutex = Util.nextMutex();
  private final StringBuilder tmpsb = new StringBuilder(20);

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
      sb.append("p=").append(port).append(" #conn=").append(nconnects).append(" blksout=").append(nblksOut);
    }
    return sb;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
    if (ss != null) {
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
  }
  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  long lastMonBlksOut;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = nblksOut - lastMonBlksOut;
    lastMonBlksOut = nblksOut;
    return monitorsb.append("IFSConnects=").append(nconnects).append("\n" + "IFSBlocksOut=").append(nb).append("\n");
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb).append(port).append(" nconnects=").append(nconnects).append("\n");
    for (IndexServerThread t : thr) {
      statussb.append(t.toStringBuilder()).append("\n");
    }
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Creates a new instance of IndexFileServer - created from the main
   *
   * @param argline The edgethread argument line for parsing
   * @param tg The EdgeThread tag
   */
  public IndexFileServer(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    port = 7979;
    int dummy = 0;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-empty")) {
        dummy = 1;
      } else {
        prt("IFS: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    setDaemon(true);
    start();

  }

  /**
   * This Thread does accepts() on the port an creates a new ClientSocket inner
   * class object to track its progress (basically hold information about the
   * client and detect writes that are hung via a timeout Thread
   */
  @Override
  public void run() {
    running = true;
    StringBuilder runsb = new StringBuilder(50);
    // Open up the listen port.  Since more than one of these might be running in different instance,
    // just loop if the address is already in use.
    int loop = 0;
    long lastStatus = System.currentTimeMillis();
    for (;;) {
      try {
        ss = new ServerSocket(port);            // Create the listener
        break;
      } catch (IOException e) {
        if (e.getMessage().contains("Address already in use")) {
          if (loop % 30 == 0) {
            prta(" ** IFS: address is in use, wait for owner to exit. port=" + port);
          }
        } else {
          prta("IFS: *** Cannot set up socket server on port=" + port);
          e.printStackTrace(getPrintStream());
        }
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      }
      loop++;
    }
    prta(Util.clear(runsb).append(" IFS: start accept loop=").append(ss.getInetAddress()).
            append("-").append(ss.getLocalSocketAddress()).append("|").append(ss));
    try {
      while (!terminate) {
        prta(" IFS: call accept()");
        try {
          Socket s = accept();
          if (terminate) {
            break;      // Do not start new thread if shutdown is going on
          }
          boolean ok = false;
          for (int i = thr.size() - 1; i >= 0; i--) {
            if (thr.get(i).isIdle() && !ok) {
              thr.get(i).setSocket(s);
              prta(Util.clear(runsb).append(" IFS: reuse i=").append(i).append("/").
                      append(thr.size()).append("=").append(thr.get(i).toStringBuilder()));
              ok = true;
            }
            if (!thr.get(i).isAlive()) {
              prta(Util.clear(runsb).append("IFS: **Removing i=").append(i).append(" ").append(thr.get(i).toStringBuilder()));
              thr.remove(i);
            }    // If its exitted remove it
          }
          if (!ok) {
            IndexServerThread c = new IndexServerThread(s);
            thr.add(c);
            prta("IFS: Added a new IndexServerThread #=" + thr.size() + " " + c.toStringBuilder());
          }
        } catch (IOException e) {
          if (e.getMessage().contains("operation interrupt") && terminate) {
            prta("IFS: terminating Interrupt found.  Exit gracefully");

          } else {
            prta("IFS: IOException on accept");
            e.printStackTrace(getPrintStream());
          }
        }
        if ((System.currentTimeMillis() - lastStatus) > 600000) {
          lastStatus = System.currentTimeMillis();
          prta(getStatusString());
        }
        try {
          sleep(2000);
        } catch (InterruptedException expected) {
        }  // limit connections rate to one every two seconds
      }
    } catch (RuntimeException e) {
      prta(tag + "IFS: RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage().contains("OutOfMemory")) {
        SendEvent.doOutOfMemory("", this);
        throw e;
      }
      terminate();
    }
    prta(tag + " IFS: is exiting");
    terminate = false;
    running = false;
  }

  public Socket accept() throws IOException {
    return ss.accept();
  }

  /**
   * This class is created to satisfy each connection to the IndexFileServer
   * port.
   *
   * @author davidketchum
   */
  public final class IndexServerThread extends Thread {

    private Socket s;
    //private boolean dbg;
    private int nblks;
    private String full;
    private final String tag;
    private final StringBuilder runsb = new StringBuilder(50);
    private final StringBuilder tmpsb = new StringBuilder(50);
    private final byte[] line = new byte[512];
    private byte[] buf = new byte[2000000];
    private ByteBuffer bb2;

    private long elapse;      // track elapse time to satisfy a request.
    private final ByteBuffer bb;

    public boolean isIdle() {
      return s == null;
    }

    public void setSocket(Socket s) {
      this.s = s;
      elapse = System.currentTimeMillis();
      interrupt();
    }

    public StringBuilder toStringBuilder() {
      synchronized (tmpsb) {
        Util.clear(tmpsb).append(tag).append(" s=").append(s).append(" #buf=").append(buf.length).
                append(" Last: nblks=").append(nblks).append(" ").append(full);
      }
      return tmpsb;
    }

    /**
     * creates a new instance of EdgeQueryThread probably from a Server of some
     * type
     *
     * @param sock Socket on which to get startup string and return response
     */
    public IndexServerThread(Socket sock) {
      s = sock;
      nblks = 0;
      tag = "[" + getName().substring(7) + "]";
      prta(Util.clear(runsb).append(tag).append(" IFS IST: cons at IFR.init()"));
      IndexFileReplicator.init();
      gov.usgs.anss.util.Util.prta("new ThreadIndexServer " + getName() + " " + getClass().getSimpleName() + " s=" + sock);
      bb2 = ByteBuffer.wrap(buf);
      bb = ByteBuffer.wrap(line);
      elapse = System.currentTimeMillis();
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      TimeoutThread timeOut = new TimeoutThread("IFS:" + this.toString(), this, 15000, 30000);
      EdgeBlock eb = new EdgeBlock();
      String node = IndexFile.getNode();
      StringBuilder nodesb = new StringBuilder(4);
      byte[] btag = new byte[14];
      StringBuilder tg = new StringBuilder(14);
      short[] mBlocks = new short[IndexFile.MAX_MASTER_BLOCKS];
      String filename = "";
      final int len = 22;
      RawDisk in = null;
      while (!terminate) {
        try {
          while (s == null) {
            try {
              sleep(250);
            } catch (InterruptedException expected) {
            }    // Wait for an assigned socket
          }
          prta(Util.clear(runsb).append(tag).append(" IFS IST: new connection from ").
                  append(s.getInetAddress().toString()).append("/").append(s.getPort()).
                  append(" node=").append(node));
          int off = 0;
          while (off < len) {
            try {
              int l = s.getInputStream().read(line, off, len - off);
              if (l < 0) {
                prta(Util.clear(runsb).append(tag).append(" IFS IST: error getting command line abort"));
                try {
                  s.close();
                } catch (IOException expected) {
                }
                s = null;
                break;
              }
              off += l;
            } catch (IOException e) {
              prta(Util.clear(runsb).append(tag).append(" IFS IST: IOexception reading command line- abort. off=").append(off).
                      append(" len=").append(len));
              e.printStackTrace(getPrintStream());
              try {
                s.close();
              } catch (IOException expected) {
              }
              s = null;
              break;
            }
          }
          if (s == null) {
            continue;
          }
          //  0 Julian date of the data to be fed back
          //  4 int Not used, 0
          //  8 char*10 tag
          //  18  char*4 node
          bb.position(0);
          int julian = bb.getInt();
          bb.getInt();
          bb.get(btag);
          Util.clear(tg);
          for (int i = 0; i < btag.length; i++) {
            if (btag[i] == 0) {
              tg.append("!");
            } else {
              tg.append((char) btag[i]);
            }
          }
          //tag = new String(btag);
          node = tg.substring(10, 14);
          Util.clear(nodesb).append(node);
          if (tg.length() > 10) {
            tg.delete(10, tg.length());
          }
          prta(Util.clear(runsb).append(tag).append(" IFS IST: got request for  node=").append(nodesb).
                  append(" julian=").append(SeedUtil.fileStub(julian)).append(" tag=").append(tg));
          filename = SeedUtil.fileStub(julian) + "_" + node.trim() + ".idx";
          prta(Util.clear(runsb).append(tag).append(" IFS IST: Open index for ").append(julian).append(" file=").append(filename));
          full = "";
          File f = null;
          for (int i = 0; i < IndexFileReplicator.getNPaths(); i++) {
            full = IndexFileReplicator.getPath(i) + filename;
            f = new File(full);
            if (f.exists()) {
              prta(Util.clear(runsb).append(tag).append(" IFS IST: Found index file desired. ").append(full));
              try {
                in = new RawDisk(full, "r");
                break;
              } catch (FileNotFoundException e) {
                prt(tag + " IFS IST: go ta file not found this is impossible");
              }
            }
          }
          if (in != null) {
            long length = 0;
            try {
              length = in.length();
              nblks = (int) (length / 512L);
            } catch (IOException e) {
              prta(Util.clear(runsb).append(tag).append(" IFS IST: Got an IOException getting length of index file!").append(f).append(e));
              nblks = 0;
            }
            prta(Util.clear(runsb).append(tag).append(" IFS IST: write ").append(nblks).
                    append(" blks  #bytes=").append(length).append(" ").append(full));
            if (nblks > 0) {     // is there anything to send?
              nblksOut += nblks;
              if (buf.length < nblks * 512) {
                buf = new byte[nblks * 3 / 2 * 512];
                bb2 = ByteBuffer.wrap(buf);
                prta(Util.clear(runsb).append(" Increase index file buffer space to ").append(buf.length));
              }
              try {
                in.seek(0L);
                in.readBlock(buf, 0, nblks * 512);
                //in.readFully(buf,0, nblks*512);
              } catch (IOException e) {
                prta(Util.clear(runsb).append(tag).append(" IFS IST: failed to read index. e=").append(e));
                nblks = 0;
              }
              try {
                in.close();
              } catch (IOException e) {
                prta(Util.clear(runsb).append(tag).append(" IFS IST: Failed to close up e=").append(e));
              }
              bb2.position(8);
              short nextIndex = bb2.getShort();
              if (nextIndex < nblks) {    // If the index file is high watered, limit how much we send.
                prta(Util.clear(runsb).append(tag).append(" IFS IST: Limit nblks to nextIndex=").
                        append(nextIndex).append(" from file len=").append(nblks));
                nblks = nextIndex;
              }
              for (int iblk = 0; iblk < nblks; iblk++) {
                try {
                  //len = in.readBlock(line, iblk, 512);
                  System.arraycopy(buf, iblk * 512, line, 0, 512);
                  // If this is the control block, bust out the list of master blocks so we
                  // can mark them as such
                  if (iblk == 0) {
                    bb.clear();
                    bb.position(10);
                    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
                      mBlocks[i] = bb.getShort();
                    }

                  }
                  // To know if this is a "active" block, check the extents.  If it is not full
                  // Then mark it so
                  int currentExtentIndex = 0;
                  // if this block is a master block, then extentIndex =-1
                  for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
                    if (mBlocks[i] == iblk) {
                      currentExtentIndex = -1;
                      break;
                    } // Its a master block
                    if (mBlocks[i] <= 0) {
                      break;          // out of master blocks, break!
                    }
                  }
                  // INDEXFILESRV replaces a seedname which is likely unknown
                  // block#=-1, indexBlock is iblk, index is currentExtentIndex, buf offset and sequence)
                  eb.set(julian, nodesb, INDEXFILESRV, -1, iblk, currentExtentIndex, line, 0, iblk);
                  s.getOutputStream().write(eb.get(), 0, EdgeBlock.BUFFER_LENGTH);
                  //prta("jul="+julian+" Wrote block="+iblk+" nd="+node);
                  timeOut.resetTimeout();
                } catch (IOException e) {
                  break;      // probably EOF!
                }
              }
            }
          }
          prta(Util.clear(runsb).append(tag).append(" ").append(filename).
                  append(" write complete nblks=").append(nblks).append(" elapse=").append(System.currentTimeMillis() - elapse));
        } catch (RuntimeException e) {
          prta(Util.clear(runsb).append("RuntimeExcp in ").append(tag).append(" e=").append(e));
          e.printStackTrace(getPrintStream());
          SendEvent.edgeSMEEvent("RunTimeMom", "Runtime in IndexFileServer e=" + e, this);
        }
        if (in != null) {
          try {
            in.close();
          } catch (IOException expected) {
          }
        }
        if (s != null) {
          try {
            s.close();
          } catch (IOException expected) {
          }
        }
        s = null;

      }   // while(!terminate)
      // If we opened this file, close it.  If not, make sure indexes get updated
      prta(Util.clear(runsb).append(tag).append(" IFS IST: Thread for file ").append(filename).append(" is exiting"));
    } // end of run()

  }

  /**
   * Unit test main()
   *
   * @param args Unused command line args
   */
  public static void main(String[] args) {
    Util.debug(false);
    IndexFile.init();
    Util.setModeGMT();
    Util.prt(Util.asctime());
    int port = 7979;
    String node = "3";
    String cwbIP = "localhost";
    int julian = SeedUtil.toJulian(2008, 40);
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-n")) {
        node = args[i + 1];
      }
      if (args[i].equals("-j")) {
        julian = Integer.parseInt(args[i + 1]);
      }

    }
    IndexFileServer server = new IndexFileServer("-p " + port + " -dbg", "IFS");
    //EdgeBlockClient ebc = new EdgeBlockClient("-p 7979 -j "+julian+" -node "+node.trim()+   // main() test
    //                " -h "+cwbIP,"DUM");
    //ArrayList<EdgeBlock> out = ebc.waitForIndex(15000);
    //if(out == null) Util.prt("There is not such index");
    //else Util.prt("Got "+out.size()+" blocks back");
  }
}
