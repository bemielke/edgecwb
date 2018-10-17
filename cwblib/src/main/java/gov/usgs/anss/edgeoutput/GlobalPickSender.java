/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.text.DecimalFormat;
import java.nio.ByteBuffer;

/**
 * represents a New Global Pick packet which has additional fields. There is a general udp header
 * followed by a payload. The payload is a single space delimited line where ? stands for omitted
 * string elements and 0.0 for omitted numeric elements. The whole message is stored in a
 * bytebuffer.
 * <br> The header on the wire is:
 * <br> 0 msgInst Message institution
 * <br> 1 msgType A type of message
 * <br> 2 modId A module ID
 * <br> 3 fragNum The packet number of the packet, 0=first
 * <br> 4 msgSeqNum The message sequence number
 * <br> 5 lastOfMsg Flag indicating this is the last packet in the message
 * <br> Global Pick:
 * <br> char[32] author String containing either EW logo (TypeModInst) or "US-SomeAuthorString",
 * limit 32 characters
 * <br> char space Delimiter
 * <br> int sequence Global pick sequence number
 * <br> char space Delimiter
 * <br> int version Global pick message version number
 * <br> char space Delimiter
 * <br> char[] station String containing station name aka site, limit 5 characters
 * <br> char space Delimiter
 * <br> char[] channel String containing channel aka component, limit 3 characters
 * <br> char space Delimiter
 * <br> char[] network String containing network code, limit 2 characters
 * <br> char space Delimiter
 * <br> char[] location String containing location code, limit 2 characters
 * <br> char space Delimiter
 * <br> char[18] pick time String containing the pick time in the form YYYYMMDDHHMMSS.mmm
 * <br> char space Delimiter
 * <br> char[8] phase name String containing the phase name, limit 8 characters
 * <br> char space Delimiter
 * <br> double error_window Double containing the symmetrical error window half width
 * <br> char space Delimiter
 * <br> char polarity Character containing the polarity U or D for up or down
 * <br> char space Delimiter
 * <br> char onset Character containing the onset, i, e, q for impulsive, emergent, questionable
 * <br> char space Delimiter
 * <br> char picker_type Character containing the picker type, m = manual, r = raypicker, l = lomax,
 * e = EW, U = UNKNOWN.
 * <br> char space Delimiter
 * <br> double high_pass Double containing the high pass frequency
 * <br> char space Delimiter
 * <br> double low_pass Double containing the low pass frequency
 * <br> char space Delimiter
 * <br> double back_azm Double containing the back azimuth
 * <br> char space Delimiter
 * <br> double slowness Double containing the slowness
 * <br> char space Delimiter
 * <br> double snr Double containing the signal to noise ratio
 * <br> char space Delimiter
 * <br> double amp Double containing the amplitude
 * <br> char space Delimiter
 * <br> double period Double containing the amplitude period
 *
 * @author U.S. Geological Survey <jpatton at usgs.gov>
 */
public final class GlobalPickSender {
  // This static area is for all of the queues

  public static final int GLOBALPICKLENGTH = 200;
  public static final int GLOBALPICKTYPE = 228;   // Per earthworm.d file
  /**
   * these are the offsets in the 70 byte header (6 bytes general header and 64 bytes TraceBuf header)
   */
  private final static int OFF_INST = 0;
  private final static int OFF_TYPE = 1;
  private final static int OFF_MOD = 2;
  private final static int OFF_FRAGNO = 3;
  private final static int OFF_SEQNO = 4;
  private final static int OFF_LAST = 5;
  private final DecimalFormat df2 = new DecimalFormat("00");
  private final DecimalFormat df3 = new DecimalFormat("000");
  private final DecimalFormat df4 = new DecimalFormat("0000");
  private final DecimalFormat df64 = new DecimalFormat("0.0000");
  private final DecimalFormat df31 = new DecimalFormat("0.0");
  private int queueSize = 1000;
  //private int queueMsgMax;
  //private final Integer queueMutex = Util.nextMutex();
  private final ArrayList<ByteBuffer> queue;
  private boolean terminate;
  private final int[] length;
  //private int [] outOfOrder;  
  private int nextin;
  private int nextout;
  private int ndiscards;
  private int maxUsed;
  private int npacket;
  private final String host;        // target broadcast addr
  private final String mhost;       // bind host
  private final int mport;          // bind port if present
  private final int port;           // target port 
  //private long lastThrottle;
  private int throttleMS = 10;
  private int institution = TraceBuf.INST_USNSN;
  private int module = 101;
  private int seq = 0;        // This is the wire sequence
  private boolean blockOutput;
  private final EdgeThread par;
  private boolean dbg;
  private final OutputThread outputer;

  @Override
  public String toString() {
    return "GPS: #pkt=" + npacket + " discards=" + ndiscards + " maxUsed=" + maxUsed + " " + host + "/" + port + " " + outputer.toString();
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public void terminate() {
    terminate = true;
  }

  public void setInstitution(int in) {
    institution = in;
  }

  public void setModule(int in) {
    module = in;
  }

  public void setBlockOutput(boolean b) {
    blockOutput = b;
  }

  /**
   * The the number of milliseconds to wait between each output packet. So setting it to 10 sets the
   * maximum possible output rate to 100 packets per second.
   *
   * @param ms Milliseconds to wait between packets.
   */
  public void setThrottleMS(int ms) {
    throttleMS = ms;
    if (ms <= 0) {
      throttleMS = 10;
    }
  }

  /**
   * This thread sets up a queue of Global Picks to be sent on a Earthworm wire to the broadcast
   * address to the given port. Optionally the local interface and port can be set as well.
   *
   * @param queueSize Number of elements that can be queued
   * @param host The IP address of the broadcast address for the sub=net
   * @param port The port to broadcast to
   * @param mhost Optional, if given this IP address is bound for sending the packets(leave null or
   * "" if no bindings)
   * @param mport Optional, if not zero, then bind this address on the sender end
   * @param parent an EdgeThread for logging, or null for console logging.
   */
  public GlobalPickSender(int queueSize, String host, int port, String mhost, int mport, EdgeThread parent) {
    this.queueSize = queueSize;
    this.host = host;
    this.port = port;
    this.mhost = mhost;
    this.mport = mport;
    par = parent;
    throttleMS = 10;
    queue = new ArrayList<>(queueSize);
    for (int i = 0; i < queueSize; i++) {
      queue.add(i, ByteBuffer.wrap(new byte[GLOBALPICKLENGTH]));
    }
    nextin = 0;
    nextout = 0;
    ndiscards = 0;
    length = new int[queueSize];
    //outOfOrder = new int[queueSize];
    outputer = new OutputThread();
  }

  /**
   * Queue a pick into this object. The return indicates whether space was available to send the
   * pick.
   *
   * @param author Character string with author name
   * @param pickTime The time of the pick
   * @param seedname NNSSSSSCCCLL for the channel being picked
   * @param amplitude in um (<=0. omit)
   * @param period in seconds (<=0. omit)
   * @param phase A phase code like "P" or "S" limit 8 characters
   * @param sequence Some sequence number for this this pick. This must be unique for each pick.
   * @param error_window This is the error window half width (<0 omit)
   * @param polarity U or D or ?
   * @param onset i, e, q for impulsive, emergent, questionable
   * @param pickerType m = manual, r = raypicker, l = lomax, e = EW, s=subspace, U = UNKNOWN. We can
   * add others if we need them
   * @param hipassFreq Frequency in Hz of high pass filter on picker (<=0. omit)
   * @param lowpassFreq Frequency in Hz of low pass filter on picker (<=0. omit)
   * @param backAzm In degrees clockwise from true north (<0. omit)
   * @param slowness The slowness (<=0. omit)
   * @param snr The estimated signal to noise ratio (<=0. omit);
   * @return True if the pick was put in the queue, otherwise the pick was not queued and user might want
   * to resubmit later!
   */
  public synchronized boolean queuePick(String author, GregorianCalendar pickTime, String seedname,
          double amplitude, double period,
          String phase, int sequence, double error_window, String polarity,
          String onset, String pickerType, double hipassFreq, double lowpassFreq, double backAzm,
          double slowness, double snr
  ) {
    //prta("QTB nin="+nextin+" "+new TraceBuf(buf));
    if ((nextin + 1) % queueSize == nextout) {
      ndiscards++;
      if (ndiscards % 20 == 1) {
        prta("Q overflow nin=" + nextin + " nout=" + nextout + " ndis=" + ndiscards + " mxuse=" + maxUsed);
      }
      if (ndiscards % 1000 == 999) {
        SendEvent.edgeSMEEvent("GPSQovfl", "Hydra Q overflow=" + ndiscards, this);
      }
      return false;
    }
    int next = (nextin + 1) % queueSize;
    npacket++;

    int version = 2; // we only produce the second version of global pick

    if (getNused() > maxUsed) {
      maxUsed = getNused();
    }
    ByteBuffer bb = queue.get(next);

    // UDP Header
    bb.position(0);
    bb.put((byte) institution);
    bb.put((byte) GLOBALPICKTYPE);
    bb.put((byte) module);
    bb.put((byte) 0);   // Frag number
    bb.put((byte) (seq++));   // Wire sequence
    bb.put((byte) 1);   // there is ever only 1 message per packet, so this is always 1

    // UDP Payload
    if (author.length() > 31) {
      author = author.substring(0, 31);
    }
    bb.put(author.trim().getBytes()); // Author string
    bb.put((byte) 32);    // Space
    bb.put((sequence + "").getBytes()); // Pick (not wire) sequence number
    bb.put((byte) 32);    // Space
    bb.put((version + "").getBytes()); // Global Pick message version
    bb.put((byte) 32);    // Space
    bb.put((seedname.substring(2, 7).trim().getBytes()));  // Station
    bb.put((byte) 32);    // Space
    bb.put((seedname.substring(7, 10).trim().getBytes())); // Channel
    bb.put((byte) 32);    // Space
    bb.put((seedname.substring(0, 2).trim().getBytes()));  // Network
    bb.put((byte) 32);    // Space
    bb.put(((seedname.substring(10) + "--").substring(0, 2).replaceAll(" ", "-").trim().getBytes()));   // Location
    bb.put((byte) 32);    // Space
    bb.put(df4.format(pickTime.get(Calendar.YEAR)).getBytes()); // Pick time
    bb.put(df2.format(pickTime.get(Calendar.MONTH) + 1).getBytes());
    bb.put(df2.format(pickTime.get(Calendar.DAY_OF_MONTH)).getBytes());
    bb.put(df2.format(pickTime.get(Calendar.HOUR_OF_DAY)).getBytes());
    bb.put(df2.format(pickTime.get(Calendar.MINUTE)).getBytes());
    bb.put(df2.format(pickTime.get(Calendar.SECOND)).getBytes());
    bb.put((byte) '.');
    bb.put(df3.format(pickTime.get(Calendar.MILLISECOND)).getBytes());
    bb.put((byte) 32);    // Space
    if (phase == null) {
      phase = "?"; // Phase code
    } else if (phase.length() > 8) {
      phase = phase.substring(0, 8);
    }
    bb.put(phase.trim().getBytes());
    bb.put((byte) 32);    // Space
    if (error_window < 0) {
      bb.put(df31.format(0).getBytes()); // Error window
    } else {
      bb.put(df64.format(error_window).getBytes());
    }
    bb.put((byte) 32);    // Space
    if (polarity == null) {
      bb.put((byte) '?');  // Polarity
    } else {
      bb.put((byte) (polarity.length() == 0 ? '?' : polarity.charAt(0)));
    }
    bb.put((byte) 32);    // Space 
    if (onset == null) {
      bb.put((byte) '?');  // Onset
    } else if (onset.length() <= 0) {
      bb.put((byte) '?');
    } else {
      bb.put((byte) onset.charAt(0));
    }
    bb.put((byte) 32);    // Space
    if (pickerType == null) {
      bb.put((byte) 'U'); // Picker Type
    } else if (pickerType.length() <= 0) {
      bb.put((byte) '?');
    } else {
      bb.put((byte) pickerType.charAt(0));
    }
    bb.put((byte) 32);    // Space
    if (hipassFreq <= 0.) {
      bb.put(df31.format(0).getBytes()); // High Pass
    } else {
      bb.put(df64.format(hipassFreq).getBytes());
    }
    bb.put((byte) 32);    // Space
    if (lowpassFreq <= 0.) {
      bb.put(df31.format(0).getBytes()); // Low Pass
    } else {
      bb.put(df64.format(lowpassFreq).getBytes());
    }
    bb.put((byte) 32);    // Space
    if (backAzm < 0.) {
      bb.put(df31.format(0).getBytes()); // Back Azimuth
    } else {
      bb.put(df64.format(backAzm).getBytes());
    }
    bb.put((byte) 32);    // Space
    if (slowness <= 0.) {
      bb.put(df31.format(0).getBytes()); // Slowness
    } else {
      bb.put(df64.format(slowness).getBytes());
    }
    bb.put((byte) 32);    // Space
    if (snr <= 0.) {
      bb.put(df31.format(0).getBytes()); // SNR
    } else {
      bb.put(df64.format(snr).getBytes());
    }
    bb.put((byte) 32);    // Space
    if (amplitude <= 0.) {
      bb.put(df31.format(0).getBytes()); // Amplitude
    } else {
      bb.put(df64.format(amplitude).getBytes());
    }
    bb.put((byte) 32);    // Space
    if (period <= 0.) {
      bb.put(df31.format(0).getBytes()); // Period
    } else {
      bb.put(df64.format(period).getBytes());
    }
    bb.put((byte) 32);    // Space
    prta("Queue: nextin=" + next + " " + new String(bb.array(), 6, bb.position() - 6));

    length[next] = bb.position();
    nextin = next;
    return true;
  }

  /**
   * get number of used buffers in the queue
   *
   * @return The number of used buffers
   */
  public int getNused() {
    int nused = nextin - nextout;         // nextout is chasing nexting
    if (nused < 0) {
      nused += queueSize;    // correct if nextin is < next out
    }
    if (nused > maxUsed) {
      maxUsed = nused;  // Keep tran of maximum 
    }
    return nused;
  }

  public final class OutputThread extends Thread {

    DatagramSocket ds = null;
    DatagramPacket dp = null;

    @Override
    public String toString() {
      return "OT: ds=" + (ds == null ? "null" : ds.toString()) + " dp=" + (dp == null ? "null" : dp.toString());
    }

    public OutputThread() {
      gov.usgs.anss.util.Util.prta("new ThreadOutput " + getName() + " " + getClass().getSimpleName());
      start();
    }

    @Override
    public void run() {
      String tag = getName();
      tag = "[" + tag.substring(tag.indexOf("-") + 1) + "]";
      //lastThrottle = System.currentTimeMillis();
      InetAddress ipaddr;

      prta(tag + " GPS: starting thread mport=" + mport + " mhost=" + mhost + " host=" + host + " port=" + port
              + " throttleMS=" + throttleMS + "/" + (400 / throttleMS));
      while (length == null) {
        try {
          sleep(100);
        } catch (InterruptedException e) {
        }
      }
      while (!terminate) {
        try {                       // catch all runtime exceptions
          while (ds == null || dp == null) {
            try {
              ipaddr = InetAddress.getByName(host);
              if (ipaddr == null) {
                prt("    ***** Host did not translate = " + host);
              }
              if (length == null) {
                prt("    ***** length=null!");
              }
              if (null == mhost) {
                ds = new DatagramSocket(mport);
              } else {
                switch (mhost) {
                  case "":
                    ds = new DatagramSocket(mport);
                    break;
                  default:
                    ds = new DatagramSocket(mport, InetAddress.getByName(mhost));
                    break;
                }
              }
              dp = new DatagramPacket(new byte[GlobalPickSender.GLOBALPICKLENGTH], length[nextout],
                      ipaddr, port);
              prta(tag + "GPS: Open UDP DP=" + host + "/" + port
                      + " mhost=" + mhost + "/" + mport + " block output=" + blockOutput
                      + " binding " + ds.getLocalSocketAddress().toString() + "/" + ds.getLocalPort());
              break;
            } catch (UnknownHostException e) {
              prta(tag + " GPS: Unknown host exception. e=" + e);
              SendEvent.debugEvent("HydUnkHost", "Hydra could not translate host=" + host, this);
              if (par == null) {
                e.printStackTrace();
              } else {
                e.printStackTrace(par.getPrintStream());
              }
            } catch (SocketException e) {
              ds = null;
              prta(tag + " GPS: cannot set up DatagramSocket to " + host + "/" + port + " bind=" + mhost + "/" + mport + " " + e.getMessage());
              SendEvent.edgeSMEEvent("HydraNoDGram", "Cannot set up DGram to " + host + "/" + port + " e=" + e, "OutputThread");
            }
            try {
              sleep(30000);
            } catch (InterruptedException e2) {
            }
          }
          if (dp == null || ds == null) {
            continue;
          }

          //EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 30000,60000);
          int lastseq = 0;
          while (!terminate) {
            if (nextin == nextout) {
              try {
                sleep(throttleMS);
              } catch (InterruptedException e) {
              }
            }
            while (nextin != nextout) {
              // Check that the sequence number from the queue are correct.
              // We are going to override the seq with one that increments by output port
              // SO I am not sure this is very useful except to debug queue/dequeue errors
              if (lastseq == 127) {
                lastseq = -129;
              }
              if (lastseq + 1 != queue.get(nextout).get(4)) {
                prta(tag + " GPS: *** Seq error got " + queue.get(nextout).get(4) + " vs " + (lastseq + 1));
              }
              lastseq = queue.get(nextout).get(4);
              if (dbg) {
                prta("GPS OUT: send nextout=" + nextout + " len=" + length[nextout]);
              }

              // check for the type of the packet to dispatch to right place
              try {
                dp.setPort(port);
                dp.setData(queue.get(nextout).array(), 0, length[nextout]);
                npacket++;
                if (!blockOutput) {
                  ds.send(dp);
                }
              } catch (IOException e) {
                prta("GPS: *** IOError e=" + e);
                if (e != null) {
                  e.printStackTrace();
                }
                break;
              }
              nextout = (nextout + 1) % queueSize;
              try {
                sleep(throttleMS);
              } catch (InterruptedException e) {
              }

            } // nextin != nextout
          }   // terminate
          if (terminate) {
            if (!ds.isClosed()) {
              ds.close();
            }
            break;
          }
        } catch (RuntimeException e) {
          prta("GPS: RuntimeException in HydraOutputer " + toString() + (e == null ? "null" : e.getMessage()));
          if (e != null) {
            e.printStackTrace();
          }
        }
      } // !terminate
      if (ds != null) {
        if (!ds.isClosed()) {
          ds.close();
        }
      }
      prta("GPS:[OutputThread] is shutdown.");
    }

  }

  /*public synchronized boolean queuePick(String author, GregorianCalendar pickTime, String seedname, double amplitude, double period,
          String phase, int sequence, double quality, String polarity, 
          String onset, String pickerType, double hipassFreq, double lowpassFreq, double backAzm,
          double slowness, double snr 
             * @param onset i, e, q for impulsive, emergent, questionable 
   * @param pickerType m = manual, r = raypicker, l = lomax, e = EW, s=subspace, U = UNKNOWN.   We can add others if we need them 
   */
  public static void main(String[] args) {
    String[] pols = {"U", "D", null};
    String[] onsets = {"i", "e", "q", null};
    String[] pickers = {"m", "r", "l", "e", "s", "U", null};
    String[] phases = {"P", "S", "pP", "sS", "PKKKKKP", null};
    Util.init("edge.prop");
    GlobalPickSender gps = new GlobalPickSender(1000, "localhost", 40000, null, 0, null);
    gps.setDebug(true);
    Util.sleep(2000);
    GregorianCalendar g = new GregorianCalendar();
    int iseq = 1;
    gps.setThrottleMS(1000);
    double amp, period, quality, hipass, lopass, backazm, slowness, snr;
    String polarity, onset, pickerType;
    for (int i = 0; i < 2002; i++) {
      boolean ok = false;
      amp = (i % 10 + 1) * 1.1;
      period = (i % 10) * .1 - 0.5;
      quality = (i % 10) * 0.05 - 0.2;
      polarity = pols[i % 3];
      onset = onsets[i % 4];
      pickerType = pickers[i % 7];
      hipass = (i % 10) * 0.5 + 1;
      lopass = hipass * 2;
      backazm = (i % 4) * 90 - 1.;
      slowness = (i % 5) * .1 - .2;
      snr = slowness * 4;
      while (!ok) {
        ok = gps.queuePick("DK", g, "USISCO BHZ00", amp, period, phases[i % 6], iseq++,
                quality, polarity, onset, pickerType, hipass, lopass, backazm, slowness, snr);
        //ok=gps.queuePick("DK",g, "USISCO BHZ00", 0.1, 11.,"P", iseq++,  0.5121, "U","i","l",0.1, 1.,-1.,-1.,2.5);

        if (!ok) {
          Util.prta("Queue pick failed i=" + i + " nused=" + gps.getNused());
        } else {
          break;
        }
        Util.sleep(500);
      }
      g.setTimeInMillis(g.getTimeInMillis() - i * 10000);
      Util.sleep(10);
    }
    Util.sleep(1000);
    gps.terminate();
    while (gps.getNused() > 0) {
      Util.prt("Sleep nused=" + gps.getNused());
    }
    Util.sleep(1000);
    System.exit(0);
  }
}
