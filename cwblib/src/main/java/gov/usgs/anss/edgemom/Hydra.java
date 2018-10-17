/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edgeoutput.HydraOutputer;
import gov.usgs.anss.edgeoutput.ChannelHolder;
import gov.usgs.anss.edgeoutput.ChannelInfrastructure;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgeoutput.*;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.*;
import gov.usgs.edge.config.Channel;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.anss.seed.MiniSeed;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * This class sets up output to Hydra, ie, sets up the service that creates
 * TraceBuf packets on a Earthworm tracewire. It does so by configuring a
 * HydraOutputer and attaching it to the ChannelInfrastructure that will handle
 * all of the hydra output. Most of the configuration arguments are actually
 * intended for HydraOutputer. This class is called statically by receivers of
 * data to put data in the ChannelInfrastructure. The ChannelInfrastructure then
 * metes the data out to the HydraOutputer when it time period has elapsed. Only
 * one of these should be set up on each EdgeMom instance.
 * <p>
 * There is alternate submitter of data (sendNoChannelInfrastructure) which can
 * be used to send data to Hydra, but should only be used if the data is always
 * mostly in order. Such data is run through an InorderChannel filter to insure
 * no data is sent out of order, but no attempt is made to put the data in
 * order.
 * <p>
 * If the installation define a channel flag named 'EdgeWire Enable', then the
 * code requires that this bit be set for all channels going to the wire.
 * Effectively this changes the default behavior that all channels go to Hydra
 * unless specifically excluded, to all channels that are not excluded, must
 * have this flag bit set. The first flag must be "EdgeWire Disable" and if this
 * bit is set in flags, the channel will not be sent out (the USGS uses this
 * bit, but does not define an EdgeWire Enable").
 * <br>
 * Note: Each hydra on any server must have a unique "inst" code across all
 * instances of edgemom. This is a Earthworm convention to make the "logos"
 * unique. The HydraOutputer thread also binds a local port for emitting the UDP
 * packets. This makes it so the viewing the UDP packets can be uniquely traced
 * back to an Instance of EdgeMom. So this "-mport" must be different for each
 * Hydra thread run on a single server.
 *
 * <br>
 * Beware of assigning the same module number to more than one Hydra thread
 * instance. Matching "logos" on Earthworm systems are sequence together so
 * multiple identical modules will not present correctly to EW. NOTE: if using
 * the derived LH feature, do not us modules above 30. When using this feature
 * 30 is added to the module of the thread for all derived LH channels so that
 * they can be sequenced separately.
 * <PRE>
 * The trace_buf earthworm packet is laid out :
 * 0  int pinno   The Pin number
 * 4 int nsamp   The number of decompressed ints in data portion
 * 8 double start The seconds since 1970 as a start time
 * 16 double end   The Seconds since 1970 as an end time
 * 24 double rate   The sample rate in hz
 * 32 String*7 station The station name portion )seed 5 character + zero_
 * 39 String*9 network The network portion (seed two character + zero)
 * 48 String*4 chan    The channel name portion (3 characters+zero)
 * 52 String*3 location The location code (2 char + zero)
 * 55 String*2 version  The version code + zero
 * 57 String*3 datatype The data type
 * 60 String*2 quality  The quality flag
 * 62 char*2   Padding
 * 64 byte[]   No more than 4096 - header length (68)
 *
 * switch   arg       Description
 * -dbg               Turn debug output on
 * -host   nn.nn.nn   IP address to transmit UDP to (should be a broadcast address def=192.168.18.255) (HydraOutput param)
 * -mhost  nn.nn.nn   The IP address to bind this UPD transmission to on this host (def=edge?-hydra.cr.usgs.gov)(HydraOutput param)
 * -port   pppp       Target output port number (def=40010) (HydraOutput param)
 * -aport   pppp      Target output port number for array data(def=40020) (HydraOutput param)
 * -ooroffset nn      Offset to selected port for out-of-order data (zero, do not send oor data)
 * -mport  pppp       Local system UDP transmitter port to bind (def=40005)(HydraOutput param) must be different for each hydra on a single server
 * -gpport pppp       Local system UDP transmitter port to bind for GlobalPicks(def=40080)(HydraOutput param)
 * -module nn         Earthworm Module ID (def=2) beware using the same module on different Hydra instances(HydraOutput param)
 * -inst   ii         Earthworm institution ID (def=13 USGS)(HydraOutput param) must be different for each sender to a wire
 * -wait   nn         Wait time in seconds for OutputInfrastructer (def=180) (HydraOutput param)
 * -qsize  nnnn       Number of message to allow in Hydra Input queue (def=10000)(HydraOutput param)
 * -msgmax nnnn       Message size max (def=4096) (HydraOutput param)
 * -nochk            If present, channel check for reasonably seismic channels is omitted and all channels are done
 * -bw    nn         Bandwidth limit out in megabits/s (default 8)
 * -state filename   Use the given file name to store the state of the InorderChannel structure across startups.
 * -dbgch  tmpseed    ChannelHolder channel to do debug printing (passed to ChannelHolder)
 * -oordbg           If set, do more logging of data for oor offset
 * -blockoutput       Do not send any actual output (HydraOutput param)
 * -deriveLHONLY     Debug flag - only send out derived LH channels, do not send any others.
 * -dlhport pp       Set the derived LH port and turn on deriving LH data (default=0 and no derived LH) 30 will be added to the module as well
 * -scn              Send data in SCN version 1.0 format and not the default SCNL Version 2.0 form
 * </PRE>
 *
 *
 *
 * @author davidketchum
 */
public final class Hydra extends EdgeThread {

  private static final TLongObjectHashMap<HydraOutputer> hydraOutputers = new TLongObjectHashMap<HydraOutputer>();
  //private static final TreeMap<String,HydraOutputer> hydraOutputers= new TreeMap<String,HydraOutputer>();   // List of channel going directly to Outputers
  private static final GregorianCalendar st = new GregorianCalendar();
  private static final GregorianCalendar exp = new GregorianCalendar();
  private static final TLongObjectHashMap<InorderChannel> inorderChannels = new TLongObjectHashMap<InorderChannel>();
  //private static final TreeMap<String, InorderChannel> inorderChannels = new TreeMap<String, InorderChannel>();;
  private static String stateFile;
  private static long edgeWireEnableMask;
  //private static DecimalFormat df1 = new DecimalFormat("0.0");

  //static TreeMap<String, Integer> chans;
  private final ShutdownHydra shutdown;
  private boolean dbg;
  private static Hydra thisHydra;
  private static boolean scn;
  private int npackets;
  private TraceBuf tb;              // scratch space for a buffer
  private final ChannelInfrastructure chanInfra;
  private static boolean noChannelCheck;
  private int seq;
  private String[] excludes;
  private static final StringBuilder tmpsb = new StringBuilder(100);
  private static final StringBuilder tmpsbo = new StringBuilder(100);

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsbo);
    }
    synchronized (sb) {
      sb.append(tag).append(" #out=").append(hydraOutputers.size()).append(" #inord=").append(inorderChannels.size());
    }
    return sb;
  }

  public ChannelInfrastructure getChannelInfrastructure() {
    return chanInfra;
  }

  public static boolean isSCN() {
    return scn;
  }
  private static final StringBuilder tmpseed = new StringBuilder(12);

  public synchronized static boolean readState() {
    if (stateFile == null) {
      return false;
    }
    try {
      RawDisk in = new RawDisk(stateFile, "r");
      thisHydra.prta(Util.clear(tmpsb).append("Hydra: read state file=").append(stateFile) + " len=" + in.length());
      byte[] b = new byte[(int) in.length()];
      ByteBuffer bb = ByteBuffer.wrap(b);
      in.readBlock(b, 0, (int) in.length());
      //byte [] s = new byte[12];
      bb.position(0);
      long ms;
      for (int i = 0; i < b.length / 20; i++) {
        //bb.get(s);
        Util.clear(tmpseed);
        for (int ii = 0; ii < 12; ii++) {
          tmpseed.append((char) bb.get());
        }
        ms = bb.getLong();
        inorderChannels.put(Util.getHashFromSeedname(tmpseed), new InorderChannel(tmpseed, ms));
        //  thisHydra.prta(Util.clear(tmpsb).append(i).append(" Hydra: New InorderChannel0=").append(tmpseed).append("|").
        //        append(Util.ascdatetime2(ms,null)));
      }
      thisHydra.prta(Util.clear(tmpsb).append("Hydra: read state file=").append(stateFile).append(" ").append(b.length / 20).append(" chans"));
    } catch (IOException e) {
      thisHydra.prta(Util.clear(tmpsb).append("Hydra: Error reading state file=").append(stateFile).append(" e=").append(e));

    }
    return true;
  }

  public synchronized static boolean writeState() {
    if (stateFile == null || inorderChannels.size() == 0) {
      return false;
    }
    byte[] b = new byte[inorderChannels.size() * 20];
    ByteBuffer bb = ByteBuffer.wrap(b);
    TLongObjectIterator<InorderChannel> itr = inorderChannels.iterator();
    while (itr.hasNext()) {
      itr.advance();
      InorderChannel c = itr.value();
      for (int i = 0; i < 12; i++) {
        bb.put((byte) c.getSeednameSB().charAt(i));
      }
      //bb.put((c.getSeedname()+"         ").substring(0,12).getBytes());
      bb.putLong(c.getLastTimeInMillis());
    }
    try {
      try (RawDisk out = new RawDisk(stateFile, "rw")) {
        out.writeBlock(0, b, 0, b.length);
        out.setLength(b.length);
      }
      if (thisHydra != null) {
        thisHydra.prt(Util.clear(tmpsb).append("Hydra: write state file=").append(stateFile).append(" ").append(b.length / 20).append(" chans"));
      } else {
        Util.prt(Util.clear(tmpsb).append("Hydra: write state file=").append(stateFile).append(" ").append(b.length / 20).append(" chans"));
      }
    } catch (IOException e) {
      Util.prta(Util.clear(tmpsb).append("Hydra: error writing state file=").append(stateFile).append(" e=").append(e));
      thisHydra.prta(Util.clear(tmpsb).append("Hydra: Error writing state file=").append(stateFile).append(" e=").append(e));
    }
    return true;
  }
  private static int npast;
  private static int nfuture;

  /**
   * runt a block through the Hydra Inorder system (generally called by
   * ChannelHolder as a final check)
   *
   * @param tb The TimeSeries block we want to send
   * @return true, if ok. Not ok, means its earlier, or too far in the future
   */
  public synchronized static boolean checkInorder(TimeSeriesBlock tb) {
    if (thisHydra == null) {
      for (int i = 0; i < 1000; i++) {
        if (thisHydra == null) {
          try {
            Thread.sleep(10);
          } catch (InterruptedException expected) {
          }
        } else {
          break;
        }
      }
      if (thisHydra == null) {
        Util.prt("Hydra: ****** checkInorder() found no hydra running!");
        return false;
      }
    }

    // Find or create the channel
    InorderChannel c = inorderChannels.get(Util.getHashFromSeedname(tb.getSeedNameSB()));
    if (c == null) {
      c = new InorderChannel(tb.getSeedNameSB(), tb.getTimeInMillis());// use packet start time so it passes below on new ones
      inorderChannels.put(Util.getHashFromSeedname(tb.getSeedNameSB()), c);
      thisHydra.prta(Util.clear(tmpsb).append("Hydra: New InorderChannel2=").append(tb.getSeedNameSB()).append("|").
              append(Util.ascdatetime2(tb.getTimeInMillis(), null)));
    }

    // Is it too early, print message and return false.
    if (tb.getTimeInMillis() - c.getLastTimeInMillis() < -1000. / tb.getRate()) {
      synchronized (st) {
        st.setTimeInMillis(tb.getTimeInMillis());
        exp.setTimeInMillis(c.getLastTimeInMillis());
        if(npast++ % 100 == 1) 
          thisHydra.prta(Util.clear(tmpsb).append(tb.getSeedNameSB()).append(" Hydra: chkInorder discard past ").
                append(Util.ascdatetime2(st, null)).append(" exp=").append(Util.ascdatetime2(exp, null)).
                append(" diff=").append(tb.getTimeInMillis() - c.getLastTimeInMillis()).append(" #past=").append(npast).append(" ").append(tb.hashCode()));
        if (npast % 1000 == 999) {
          new RuntimeException("checkInorder npast=" + npast + " ").printStackTrace(thisHydra.getPrintStream());
        }
      }
      return false;
    }

    // Try to update the time, if it is too far in the future, it will return false and we will too after a message
    if (c.setLastTimeInMillis(tb.getNextExpectedTimeInMillis())) {
      return true;
    } else {
      if(nfuture % 100 == 1) {
        if (thisHydra != null) {
          thisHydra.prta(Util.clear(tmpsb).append(tb.getSeedNameSB()).
                  append(" Hydra: chkInorder discard future off=").
                  append(System.currentTimeMillis() - tb.getNextExpectedTimeInMillis()).
                  append(" #fut=").append(nfuture));
        } else {
          Util.prta(Util.clear(tmpsb).append(tb.getSeedNameSB()).append(" Hydra: checkInorder discard future ").
                  append(tb.toStringBuilder(null)).append(" off=").append(System.currentTimeMillis() - tb.getNextExpectedTimeInMillis()).
                  append(" #fut=").append(nfuture));
        }
      }
      nfuture++;
      if (nfuture % 1000 == 999) {
        new RuntimeException("checkInorder nfuture=" + nfuture).printStackTrace(thisHydra.getPrintStream());
      }
      throw new RuntimeException("Packet from future");
    }
    //return false;
  }

  /**
   * set data to hydra output queues without going through the channel
   * infrastructures. Doing this there is no buffering of the data to try to put
   * any data back in order, it is just shipped if it is later than the prior
   * data (i.e. no out of order data is every shipped). The module, institution,
   * etc will be set by the switch passed to the channel infrastructure.
   *
   * @param tb A TimeSeriesBlock (MiniSeed or TraceBuf) ready to be sent.
   */
  public synchronized static void sendNoChannelInfrastructure(TimeSeriesBlock tb) {
    if (hydraOutputers == null) {
      return;
    }
    if (thisHydra == null) {
      return;
    }
    if (tb.getNsamp() <= 0) {
      return;    // Hydra does not need this
    }
    if (tb.getRate() <= 0.) {
      return;    // Hydra does not like this!
    }
    if (!okChannelToHydra(tb.getSeedNameSB())) {
      return;
    }
    Channel ch = EdgeChannelServer.getChannel(tb.getSeedNameSB());
    if (ch == null) {
      return;
    }
    //if( (ch.getFlags() & 1) != 0) thisHydra.prta("check flags3 "+ch.getChannel()+" "+ch.getFlags());
    if ((ch.getFlags() & 1) != 0) {
      return;        // Is it on the Hydra disable mask
    }
    HydraOutputer ho = hydraOutputers.get(Util.getHashFromSeedname(tb.getSeedNameSB()));
    InorderChannel c = inorderChannels.get(Util.getHashFromSeedname(tb.getSeedNameSB()));
    if (c == null) {
      c = new InorderChannel(tb.getSeedNameSB(), tb.getTimeInMillis());
      inorderChannels.put(Util.getHashFromSeedname(tb.getSeedNameSB()), c);
      if (thisHydra != null) {
        thisHydra.prta(Util.clear(tmpsb).append("Hydra: New InorderChannel3=").append(tb.getSeedNameSB()).append("|").
                append(Util.ascdatetime2(tb.getTimeInMillis(), null)));
      }
    }
    if (ho == null) {
      // let the Hydra process start and set the arguments first
      ho = new HydraOutputer(tb.getSeedNameSB(), 0, 0L, tb.getRate());
      HydraOutputer.setParent(thisHydra);
      hydraOutputers.put(Util.getHashFromSeedname(tb.getSeedNameSB()), ho);
    }
    if (tb.getTimeInMillis() - c.getLastTimeInMillis() < -1000. / tb.getRate()) {
      synchronized (st) {
        st.setTimeInMillis(tb.getTimeInMillis());
        exp.setTimeInMillis(c.getLastTimeInMillis());
        if (tb.getTimeInMillis() - c.getLastTimeInMillis() > -600000) {
          if(npast++ % 100 == 1) {
            if (thisHydra != null) {
              thisHydra.prta(Util.clear(tmpsb).append(tb.getSeedNameSB()).append(" Hydra: OOR from near discard past ").
                      append(Util.ascdatetime2(st, null)).append(" exp=").
                      append(Util.ascdatetime2(exp, null)).
                      append(" diff=").append(tb.getTimeInMillis() - c.getLastTimeInMillis()).
                      append(" #past=").append(npast));
            } else {
              Util.prta(Util.clear(tmpsb).append(tb.getSeedNameSB()).append(" Hydra: OOR from near past ").
                      append(Util.ascdatetime2(st, null)).append(" exp=").append(Util.ascdatetime2(exp, null)).
                      append(" diff=").append(tb.getTimeInMillis() - c.getLastTimeInMillis()).
                      append(" #past=").append(npast));
            }
          }
        }
      }
      //HACK: hydra ignores out of order anyway
      //ho.queue(tb,1);     // Send via out-of-order
      return;
    }
    if (c.setLastTimeInMillis(tb.getNextExpectedTimeInMillis())) {
      ho.queue(tb, 0);
    } else {
      if(nfuture++ % 100 == 0) {
        if (thisHydra != null) {
          thisHydra.prta(Util.clear(tmpsb).append(tb.getSeedNameSB()).
                  append(" Hydra: inorder discard future packet ").append(tb.toStringBuilder(null)).append(" off=").
                  append(System.currentTimeMillis() - tb.getNextExpectedTimeInMillis()).
                  append(" #fut=").append(nfuture));
        } else {
          Util.prta(Util.clear(tmpsb).append(tb.getSeedNameSB()).append(" Hydra: inorder discard future packet ").
                  append(tb.toStringBuilder(null)).append(" off=").append(System.currentTimeMillis() - tb.getNextExpectedTimeInMillis()).
                  append(" #fut=").append(nfuture));
        }
      }
    }

  }

  /**
   * set data to hydra output queues without going through the channel
   * infrastructures. Doing this there is no buffering of the data to try to put
   * any data back in order, it is just shipped. The module, institution, etc
   * will be set by the switch passed to the channel infrastructure. Do not use
   * this module if the bufs are not strictly in time order or Hydra will have
   * indigestion. Convert to traceBuf and use the other method.
   *
   * @param buf A tracebuf ready to be sent.
   * @param len The length of the tracebuf
   */
  /*public synchronized static void sendNoChannelInfrastructure(byte [] buf, int len) {
    String tmpseed=TraceBuf.getSeedname(buf);
    Channel ch = EdgeChannelServer.getChannel(tmpseed);
    if(ch == null) return;
    //if( (ch.getFlags() & 1) != 0) thisHydra.prta("check flags2 "+ch.getChannel()+" "+ch.getFlags());
    if( (ch.getFlags() & 1) != 0) return;        // Is it on the Hydra disable mask
    HydraOutputer ho = hydraOutputers.get(tmpseed);
    if(ho == null) {
      ho = new HydraOutputer(tmpseed, 0, 0L, TraceBuf.getRate(buf));
      hydraOutputers.put(tmpseed, ho);
    }
    ho.queue(buf, len);
  }*/

  /**
   * Send a mini seed packet to hydra using the ChannelInfrastructure
   * wait-for-the-bus concept
   *
   * @param ms The miniseed data to send
   */
  public synchronized static void send(TimeSeriesBlock ms) {
    //Channel c = EdgeChannelServer.getChannel(name);
    if (thisHydra != null) {
      if (ms.getNsamp() <= 0) {
        return;    // Hydra does not need this
      }
      if (ms.getRate() <= 0.) {
        return;    // Hydra does not like this!
      }
      Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
      if (c == null) {
        return;
      }
      //if( (c.getFlags() & 1) != 0) thisHydra.prta("check flags5 "+c.getChannel()+" "+c.getFlags());
      if ((c.getFlags() & 1) != 0) {
        return;        // Is it on the Hydra disable mask
      }
      if (!okChannelToHydra(ms.getSeedNameSB())) {
        //thisHydra.prta("Reject "+ms.getSeedNameString()+" in okToHydra");
        return;
      }

      try {
        thisHydra.getChannelInfrastructure().addData(ms);
      } catch (RuntimeException e) {
        thisHydra.prta(Util.clear(tmpsb).append("Got a RuntimeException in Hydra.send(MiniSeed) ").append(e == null ? "null3" : e));
        if (e != null) {
          e.printStackTrace(thisHydra.getPrintStream());
        }
      }
    }

  }

  public synchronized static void send(StringBuilder name, int year, int doy, int secs, int micros, int nsamp, int[] ts, double rate) {
    //Channel c = EdgeChannelServer.getChannel(name);
    try {
      MasterBlock.checkSeedName(name);
    } catch (IllegalSeednameException e) {
      thisHydra.prta(Util.clear(tmpsb).append("Hydra: bad seedname in send ()e=").append(e.getMessage()));
      e.printStackTrace(thisHydra.getPrintStream());
      return;
    }
    if (thisHydra == null) {
      return;      // NO place to send it!
    }
    if (nsamp == 0) {
      return;            // Hydra does not need this
    }
    if (rate <= 0.) {
      return;             // Hydra does not like this
    }
    Channel c = EdgeChannelServer.getChannel(name);
    if (c == null) {
      return;
    }
    //if( (c.getFlags() & 1) != 0) thisHydra.prta("check flags4 "+c.getChannel()+" "+c.getFlags());
    if ((c.getFlags() & 1) != 0) {
      return;        // Is it on the Hydra disable mask
    }    // If the EdgeWire Enable flag is being used (is defined), then channels must be individually enabled
    if (edgeWireEnableMask != 0 && (c.getFlags() & edgeWireEnableMask) == 0) {
      return;
    }
    if (!okChannelToHydra(name)) {
      return;
    }
    try {
      thisHydra.queue(name, year, doy, secs, micros, nsamp, ts, rate);
    } catch (RuntimeException e) {
      thisHydra.prt(Util.clear(tmpsb).append("Got a RuntimeException in Hydra.send(RTMS) ").append(e == null ? "null2" : e.getMessage()));
      if (e != null) {
        e.printStackTrace(thisHydra.getPrintStream());
      }
    }
  }
  //private static final StringBuilder params = new StringBuilder(12).append("PARAMS      ");
  private static final long params = Util.getHashFromSeedname("PARAMS      ");

  /**
   * send parameter data - all of this goes through the "PARAMS" hydra outputer
   *
   * @param type THe EW type of the data (TraceBuf2=19, TraceBuf1=20,
   * GlobalPick=228 (-28)
   * @param buf The buffer with the 6 byte logo + Udp header (payload starts in
   * byte 6
   * @param len The total length of the buffer to send.
   */
  public synchronized static void send(int type, byte[] buf, int len) {
    HydraOutputer ho = hydraOutputers.get(params);

    if (ho == null) {
      // let the Hydra process start and set the arguments first
      ho = new HydraOutputer("PARAMS      ", 0, 0L);
      HydraOutputer.setParent(thisHydra);
      hydraOutputers.put(params, ho);
    }
    ho.queue(type, buf, len);
  }
  private static final StringBuilder tmpname = new StringBuilder(12);

  public static boolean okChannelToHydra(String name) {
    Util.clear(tmpname).append(name);
    return okChannelToHydra(tmpname);
  }

  public synchronized static boolean okChannelToHydra(StringBuilder name) {
    if (noChannelCheck) {
      return true;
    }
    if (name.length() < 10) {
      thisHydra.prta(Util.clear(tmpsb).append("Bad name to okChannelToHydra=").append(name));
      return false;
    }
    char ch = name.charAt(7);
    if (ch != 'B' && ch != 'S' && ch != 'H' && ch != 'L' &&/* ch != 'V' &&*/ ch != 'M'
            && ch != 'E' && ch != 'b' && ch != 'h' && ch != 'C' && ch != 'D') {
      return false;
    }
    char ch1 = name.charAt(8);
    if (ch1 != 'L' && ch1 != 'H' && ch1 != 'D' && ch1 != 'N') {   // the D is for hyroacoustic data for PTWC
      return false;
    }
    if (ch == 'L' && ch1 == 'N') {
      return false;
    } else if (ch == 'L' && ch1 == 'D' && name.charAt(9) == 'O') {
      return false;
    } else if (name.charAt(9) == 'F') {
      return false;
    } else if (name.substring(0, 2).equals("XX")) {
      return false;
    }
    //if(name.substring(0,2).equals("IU") && name.substring(7,10).equals("BDO")) return false;
    return true;
  }

  public synchronized void queue(StringBuilder name, int year, int doy, int secs, int micros, int nsamp, int[] ts, double rate) {
    try {
      MasterBlock.checkSeedName(name);
    } catch (IllegalSeednameException e) {
      thisHydra.prta(Util.clear(tmpsbo).append("Hydra: bad seedname in queue() e=").append(e.getMessage()));
      e.printStackTrace(thisHydra.getPrintStream());
      return;
    }
    Channel c = EdgeChannelServer.getChannel(name);
    if (c == null) {
      return;
    }
    //if( (c.getFlags() & 1) != 0) thisHydra.prta("check flags "+c.getChannel()+" "+c.getFlags());
    if ((c.getFlags() & 1) != 0) {
      return;        // Is it on the Hydra disable mask
    }
    int[] ymd = SeedUtil.ymd_from_doy(year, doy);
    GregorianCalendar start = new GregorianCalendar(ymd[0], ymd[1] - 1, ymd[2]);
    start.add(Calendar.MILLISECOND, secs * 1000 + micros / 1000);

    int offset = 0;
    while (offset < nsamp) {
      int ns = nsamp - offset;
      if (nsamp - offset > TraceBuf.TRACE_MAX_NSAMPS) {
        ns = TraceBuf.TRACE_MAX_NSAMPS;
      }
      if (tb == null) {
        tb = new TraceBuf();
      }
      tb.setData(name, start.getTimeInMillis(), ns, rate, ts, offset,
              TraceBuf.INST_USNSN, TraceBuf.MODULE_EDGE, seq++, c.getID());
      offset += ns;
      start.add(Calendar.MILLISECOND, (int) (ns / rate * 1000. + 0.5));
      chanInfra.addData(tb);
      npackets++;
    }
  }

  /**
   * set debug state
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append("Hydra #pkt=").append(HydraOutputer.getNpacketStatus()).append(" ").append(chanInfra.toString())/*+"\n"+ChannelHolder.getSummary()*/;
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb.append("HydraNPkt=").append(HydraOutputer.getNpacketStatus()).append("\n").append(chanInfra.getMonitorString());
  }

  /**
   * return console output - this is fully integrated so it never returns
   * anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline The EdgeThread args
   * @param tg The EdgeThread tag
   */
  public Hydra(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    //chans = new TreeMap<String, Integer>();
    if (!EdgeChannelServer.exists()) {
      new EdgeChannelServer("-empty", "echn");
    }
    if (thisHydra != null) {
      prta("Exception Hydra created more than once!");
    }
    thisHydra = (Hydra) this;
    String[] args = argline.split("\\s");
    dbg = false;
    int dummy;
    for (int i = 0; i < args.length; i++) {
      if (args[i].length() < 1) {
        continue;
      }
      if (args[i].substring(0, 1).equals(">")) {
        break;
      }
      //prt(i+" arg="+args[id]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-nochk")) {
        noChannelCheck = true;
      } // These are HydraOutputer switches
      else if (args[i].equals("-host")) {
        i++;       // Hydra outputer switches
      } else if (args[i].equals("-mhost")) {
        i++;
      } else if (args[i].equals("-port")) {
        i++;
      } else if (args[i].equals("-mport")) {
        i++;
      } else if (args[i].equals("-module")) {
        i++;
      } else if (args[i].equals("-inst")) {
        i++;
      } else if (args[i].equals("-wait")) {
        i++;
      } else if (args[i].equals("-qsize")) {
        i++;
      } else if (args[i].equals("-ooroffset")) {
        i++;
      } else if (args[i].equals("-oordbg")) {
      } else if (args[i].equals("-msgmax")) {
        i++;
      } else if (args[i].equals("-bw")) {
        i++;
      } else if (args[i].equals("-dbgch")) {
        ChannelHolder.setDebugSeedname(args[i + 1]);
        i++;
      } else if (args[i].equals("-blockoutput")) {
      } // Hydra outputer switches
      else if (args[i].equals("-deriveLHONLY")) {
      } // Hydra outputer does this
      else if (args[i].equalsIgnoreCase("-dlhport")) {
        i++;
      } // Hydra outputer does this
      else if (args[i].equals("-scn")) {
        scn = true;
      } else if (args[i].equals("-state")) {
        stateFile = args[i + 1];
        i++;
        readState();
      } else if (args[i].equals("-empty")) {
      } else {
        prt(Util.clear(tmpsbo).append("Hydra: unknown switch=").append(args[i]).append(" ln=").append(argline));
      }
    }
    for (int i = 0; i < 30; i++) {
      if (!EdgeChannelServer.isValid()) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      } else {
        prta(Util.clear(tmpsbo).append("Hydra: EdgeChannelServer is ready after ").append(i).append(" secs"));
        break;
      }
    }
    if (EdgeChannelServer.isValid()) {
      edgeWireEnableMask = EdgeChannelServer.getFlagMask("EdgeWire Enable");
    } else {
      prta(Util.clear(tmpsbo).append("Hydra: cannot find EdgeWire Enable mask"));
    }

    tb = new TraceBuf();
    chanInfra = new ChannelInfrastructure("HydraCI", 300, 3, HydraOutputer.class, 240, this);
    HydraOutputer.setCommandLine(argline);
    prta(Util.clear(tmpsbo).append("Hydra: created args=").append(argline).append(" tag=").
            append(tag).append(" edgeWireEnablemask=").append(edgeWireEnableMask));
    shutdown = new ShutdownHydra();
    Runtime.getRuntime().addShutdownHook(shutdown);

    start();
  }

  @Override
  public void run() {
    thisHydra = this;
    running = true;
    //ArrayList<String> disables= new ArrayList<String>(100);
    while (!terminate) {
      /*Iterator<Channel> itr = EdgeChannelServer.getIterator();    // This is certainly some dead code as disables cannot be used anywhere!
      if(itr != null) {
        try {
          disables.clear();
          while(itr.hasNext()) {
            Channel c = itr.next();
            if( (c.getSendtoMask() & 32) != 0) disables.add(c.getChannel());
          }
          if(excludes == null) excludes = new String[disables.size()];
          else if(excludes.length != disables.size()) excludes = new String[disables.size()];
          for(int i=0; i<disables.size(); i++) excludes[i] = disables.get(i);

        }
        catch(ConcurrentModificationException e) {
          prt("Update of disabled units failed on conncurrentModification.  Try again later");
        }
      }*/
      prta(HydraOutputer.queueSummary());
      try {
        sleep(60000);
      } catch (InterruptedException expected) {
      }
      //prt(Util.getThreadsString());
    }
    running = false;
    chanInfra.shutdown();
    prta("Hydra Thread exiting");
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit
   */
  class ShutdownHydra extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run()
     * thread
     */
    public ShutdownHydra() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta("Hydra: Hydra Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      chanInfra.shutdown();
      if (stateFile != null) {
        prta("Hydra: shutdown() write state");
        writeState();
      }
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
      }
      TLongObjectIterator itr = hydraOutputers.iterator();
      while (itr.hasNext()) {
        itr.advance();
        HydraOutputer out = (HydraOutputer) itr.value();
        out.shutdown();
      }
      thisHydra = null;
      prta("Hydra: Shutdown() of Hydra is complete.");
    }
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    String logpath = Util.getProperty("logfilepath");
    Util.setModeGMT();
    Util.setNoInteractive(false);
    EdgeThread.setUseConsole(true);

    String line = "";
    for (String arg : args) {
      line += arg + " ";
    }
    if (line.trim().equals("")) {
      line = "-dbgch USISCO_BHZ00 -module 15 -host 192.168.7.111 -mhost 192.168.7.103 -port 16099 -mport 40099";
    }
    //line="-dbgch USISCO_BHZ00 -mhost localhost -mport 40055 -port 16099 -host localhost  >>hydra";  // DEBUG
    Util.prt("Line=" + line);
    Hydra hydra = new Hydra(line, "hydra");
    int[] data = new int[4000];
    for (int i = 0; i < data.length; i++) {
      data[i] = i;
    }

    int[] ymd = SeedUtil.ymd_from_doy(2007, 100);
    GregorianCalendar start = new GregorianCalendar(ymd[0], ymd[1] - 1, ymd[2]);

    // try {Thread.sleep(60000);} catch(InterruptedException expected) {}
    int sec = 7200;
    int nsamp = 4000;
    int micros = 101000;
    double rate = 40.;
    TraceBuf tb = new TraceBuf();
    String name = "USISCO BHZ00";
    int seq = 0;
    for (;;) {
      //start.add(Calendar.MILLISECOND, 100000);

      int offset = 0;
      while (offset < nsamp) {
        int ns = nsamp - offset;
        if (nsamp - offset > TraceBuf.TRACE_MAX_NSAMPS) {
          ns = TraceBuf.TRACE_MAX_NSAMPS;
        }
        tb.setData(name, start.getTimeInMillis(), ns, rate, data, offset,
                TraceBuf.INST_USNSN, TraceBuf.MODULE_EDGE, seq++, 20);
        offset += ns;
        start.add(Calendar.MILLISECOND, (int) (ns / rate * 1000. + 0.5));
        Hydra.sendNoChannelInfrastructure(tb);
      }
      hydra.prt("Queue USISCO BHZ for 2007,100," + sec);
      //hydra.queue("USISCO BHZ  ",2007, 100, sec, 101000, 4000, data, 40.);
      sec = sec + 100;
      try {
        Thread.sleep(2000);
      } catch (InterruptedException expected) {
      }

    }
  }
}
