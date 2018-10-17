/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.util.LinkedList;
;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.edgethread.*;
import gov.usgs.anss.edgemom.Hydra;
import gnu.trove.map.hash.TLongObjectHashMap;

/**
 * This class attempts to deal with the out-of-order data from sources like CD1.?. There is an
 * "nextExpect" packet. The rules are :
 *
 * 1) If the submitted is next, send it immediately. Check spans to see if one of them is next, 2)
 * If the submitted is two minutes newer than nextExpected, it is the new nextExpected (that is leap
 * forward if something comes in that is 2 minutes newer) 3) If neither, try to add this packet to
 * any existing spans (on beginning or end) 4) If no spans are right for addition, create a new span
 * with this packet.
 *
 * The "process()" method is used to clean up spans that are old. Basically it: 1) Tries pair wise
 * to find spans that should be combined (i.e. they have grown to each other) 2) It looks to see if
 * the span has not been added to in UPDATE_TIMEOUT ms, if so, it is sent out (basically if
 * something is not being added to, a timeout to force it out) 3) If the span is still growing but
 * has reach NSAMP_FORCEOUT samples in length, force it out. This insures long spans which might
 * grow for a long time are periodically forced through so the storage burden here is manageable.
 *
 * @author davidketchum
 */


public final class OORChan {

  private static final int UPDATE_TIMEOUT = 120000;   // # ms a span goes with no updates 
  private static final int NSAMP_FORCEOUT = 5000;     // # of samples which trigger a force out
  private static final TLongObjectHashMap<OORChan> chans = new TLongObjectHashMap<>();
  //private static final TreeMap<String, OORChan> chans = new TreeMap<String, OORChan>();
  private static OORChanServer oorServer;
  private static int number;
  private long nextExpected;
  private final LinkedList<Span> spans;
  private final StringBuilder seedname = new StringBuilder(12);
  private boolean dbg;
  private double rate;
  private final RTMSBuf buf;
  private final EdgeThread parent;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public static void setDebug(String seed, boolean b) {
    OORChan oor = chans.get(Util.getHashFromSeedname(seed));
    if (oor != null) {
      oor.setDebug(b);
    }
  }

  public void setDebug(boolean b) {
    if (dbg != b) {
      parent.prta("OORChan: " + seedname + " set debug to " + b);
    }
    dbg = b;
  }

  /**
   * Creates a new instance of OORChan
   *
   * @param name a 12 character seedname
   * @param par The parent to use for logging
   */
  public OORChan(StringBuilder name, EdgeThread par) {
    spans = new LinkedList<>();
    parent = par;
    Util.clear(seedname).append(name);
    Util.rightPad(seedname, 12);
    nextExpected = -1;        // No output for this one yet.
    dbg = false;
    chans.put(Util.getHashFromSeedname(seedname), (OORChan) this);
    buf = new RTMSBuf(-chans.size());    // create some RMTSBuf space not on the free list
    number++;
    parent.prta("OORChan: new channel " + name + " bf.ind=" + buf.getIndex() + " number=" + number);
    if (oorServer == null) {
      oorServer = new OORChanServer(7208);
    }
  }

  /**
   * return number of spans held in the OORChan
   *
   * @return The number of spans being held by the OORChan
   */
  public int getNSpans() {
    return spans.size();
  }

  /**
   * convert a string representing this object. It consists of name, rate, start time, # spans and a
   * list of the spans and their ranges, #samp
   *
   * @return s
   */
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
      sb.append("OORChan status ").append(seedname).append(" next=").append(Util.asctime(nextExpected)).
              append(" rt=").append(rate).append(" nspans=").append(spans.size()).
              append(" dbg=").append(dbg).append("\n");
      for (int i = 0; i < spans.size(); i++) {
        sb.append("   ").append(i).append(" ").
                append(seedname).append(" ").append(((Span) spans.get(i)).toStringBuilder(null)).append("\n");
      }
    }
    return sb;
  }

  /**
   * convert a string representing this object. It consists of name, rate, start time, # spans and a
   * list of the spans and their ranges, #samps
   *
   * @return The buffer detail string
   */
  public StringBuilder toStringBufDetail() {
    //StringBuilder sb = new StringBuilder(1000);
    synchronized (tmpsb) {
      Util.clear(tmpsb).append("OORChan status ").append(seedname).append(" next=").append(Util.asctime(nextExpected)).
              append(" rt=").append(rate).append(" nspans=").append(spans.size()).append("\n");
      for (int i = 0; i < spans.size(); i++) {
        tmpsb.append("   ").append(i).append(" ").append(seedname).append(" ").
                append(((Span) spans.get(i)).toString()).append("\n");
        Iterator<RTMSBuf> itr = spans.get(i).getSpanLinkedList().iterator();
        while (itr.hasNext()) {
          tmpsb.append("      ").append(itr.next().toStringBuilder(null)).append("\n");
        }
      }
    }
    return tmpsb;
  }

  /**
   * Attempt to add some data to this channel. It will either be sent, or added to a span. Hydra is
   * assumed true.
   *
   * @param ts Array of ints with time series
   * @param seedname A seedname of the channel
   * @param nsamp Number of samples in x
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param rt Digitizing rate in Hz.
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The clock quality as defined in SEED volume
   * @param timingQuality The timing quality as defined in SEED volume
   */
  public synchronized void addBuffer(int[] ts, StringBuilder seedname, int nsamp,
          int year, int doy, int sec, int micros, double rt,
          int activity, int IOClock, int quality, int timingQuality) {
    addBuffer(ts, seedname, nsamp, year, doy, sec, micros, rt, activity, IOClock, quality, timingQuality, true);
  }

  /**
   * Attempt to add some data to this channel. It will either be sent, or added to a span.
   *
   * @param ts Array of ints with time series
   * @param seedname A seedname of the channel
   * @param nsamp Number of samples in x
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param rt Digitizing rate in Hz.
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The clock quality as defined in SEED volume
   * @param timingQuality The timing quality as defined in SEED volume
   * @param hydra If true, send block to hydra
   */
  public synchronized void addBuffer(int[] ts, StringBuilder seedname, int nsamp,
          int year, int doy, int sec, int micros, double rt,
          int activity, int IOClock, int quality, int timingQuality, boolean hydra) {
    rate = rt;
    try {
      MasterBlock.checkSeedName(seedname);
    } catch (IllegalSeednameException e) {
      parent.prta("OORChan: " + seedname + " Illegal seedname in OORChan.addBuffer blow off " + e.getMessage());
      e.printStackTrace();
      return;
    }
    if (seedname.indexOf("IMPD01") >= 0) {
      dbg = true;
    }

    // Some input day used year doy and the offset may lap into next day, if so fix it here
    while (micros > 1000000) {
      micros -= 1000000;
      sec++;
    }
    while (sec >= 86400) {      // Do seconds say its a new day?
      parent.prta(Util.clear(tmpsb).append("OORChan: day adjust new yr=").append(year).
              append(" doy=").append(doy).append(" sec=").append(sec).append(" usec=").append(micros).
              append(" seedname=").append(seedname));
      int jul = SeedUtil.toJulian(year, doy);
      sec -= 86400;
      jul++;
      int[] ymd = SeedUtil.fromJulian(jul);
      year = ymd[0];
      doy = SeedUtil.doy_from_ymd(ymd);
      parent.prta(Util.clear(tmpsb).append("OORChan: day adjust new yr=").append(year).
              append(" doy=").append(doy).append(" sec=").append(sec).append(" seedname=").append(seedname));
    }
    long start = RTMSBuf.getTimeInMillis(year, doy, sec, micros); // get start time in millis of this packet
    long diff = start - nextExpected;
    if (dbg) {
      parent.prta(Util.clear(tmpsb).append("OORChan: ").append(seedname).
              append(" buf nextExpected=").append(nextExpected).append(" start=").append(start).
              append(" df=").append(start - nextExpected).append(" ").append(Util.asctime2(start)));
    }
    if (nextExpected == -1 || Math.abs(nextExpected - start) < (500. / rate + .5)
            // if diff is 2 minutes into the future but not more the 5 minutes  ahead of system time
            || (diff > 120000 && (System.currentTimeMillis() - start) > -300000)) {   // its much newer use it
      if (dbg) {
        parent.prta(Util.clear(tmpsb).append("OORChan: ").append(seedname).append(start - nextExpected).
                append(" send latest").append(diff > 1 ? "*" : "").append(diff > 120000 ? "*" : "").append(diff > 1100000000 ? "*" : ""));
      }
      RawToMiniSeed.addTimeseries(ts, nsamp, seedname, year, doy, sec,
              micros, rate, activity, IOClock, quality, timingQuality, parent);
      if (hydra) {
        Hydra.send(seedname, year, doy, sec, micros, nsamp, ts, rate);
      }

      nextExpected = start + ((long) (nsamp * 1000. / rate + 0.5));

      // see if any of the spans are now up to go.
      boolean found = true;
      while (found) {
        found = false;
        for (int i = 0; i < spans.size(); i++) {
          Span s = (Span) spans.get(i);
          if (Math.abs(s.getFirstTime() - nextExpected) < 500. / rate) {
            if (dbg) {
              parent.prt(Util.clear(tmpsb).append("OORChan: ").append(seedname).append(" send aft ").append(s.toStringBuilder(null)));
            }
            long n = s.sendToRTMS();
            if (n > 0) {
              nextExpected = n;
            }
            found = true;
            spans.remove(s);
            break;
          }
        }
      }
      return;
    }
    // Its not in order add it to the queue and process any that now line up with the end
    buf.setRTMSBuf(ts, seedname, nsamp, year, doy, sec, micros, rt, activity, IOClock, quality,
            timingQuality);
    boolean found = false;
    for (Span sp1 : spans) {
      if (sp1.add(buf)) {
        if (dbg) {
          parent.prt(Util.clear(tmpsb).append("OORChan: ").append(seedname).append(" Add buf=").append(buf).
                  append(" to span=").append(sp1.toStringBuilder(null)));
        }
        found = true;
        break;
      }
    }
    if (!found) {
      spans.add(new Span(buf));
    }
  }

  /**
   * this is called periodically to purge out any un-updated or sufficiently long spans. 1) sees if
   * any are now on the current span, 2) see if any have timed out (UPDATE_TIMEOUT) , 3) see if any
   * have nsamps above the NSAMP_FORCE limit
   */
  public synchronized void process() {
    if (spans.isEmpty()) {
      return;
    }
    boolean changed = true;
    String status = toString();
    boolean any = false;
    //parent.prt("OOR: proc() status="+toString());
    //parent.prt("OORChan Start process for "+toString());
    if (spans.size() >= 2) {
      while (changed) {
        changed = false;
        // attempt to combine all of the current spans
        for (int i = 0; i < spans.size() - 1; i++) {
          for (int j = i + 1; j < spans.size(); j++) {
            if (spans.get(j).combine(spans.get(i))) {      // Note: if true all of spans.get(i) RTMSBufs are now in spans.get(j)       
              //if(dbg) 
              parent.prta(Util.clear(tmpsb).append("OORChan: proc() ").append(seedname).
                      append(" combine ").append(i).append(" ").append(j).append(" ").
                      append(spans.get(j).toStringBuilder(null)).append("<-").append(spans.get(i).toStringBuilder(null)));
              spans.remove(i);
              parent.prt(toStringBuilder(null));
              changed = true;
              any = true;
              break;
            }
          }
        }
      }
      if (any && dbg) {
        parent.prt(status + "now\n" + toString());
      }
    }

    // Are any of these spans that are old enough to force out
    Object[] sp = spans.toArray();
    for (Object sp1 : sp) {
      // Has this span just "backed" onto the last data
      if (Math.abs(((Span) sp1).getFirstTime() - nextExpected) < 500. / ((Span) sp1).rate) {
        if (dbg) {
          parent.prt(Util.clear(tmpsb).append("OORChan: proc() ").append(seedname).
                  append(" span is at expected ").append(((Span) sp1).toStringBuilder(null)));
        }
        long n = ((Span) sp1).sendToRTMS();
        if (n > nextExpected) {
          nextExpected = n;
        }
        spans.remove((Span) sp1); // take it off the linked list
      }
      // is updating of this span stopped for 1/2 hour
      if (System.currentTimeMillis() - ((Span) sp1).getLastUpdate() > UPDATE_TIMEOUT) {
        if (dbg) {
          parent.prt(Util.clear(tmpsb).append("OORChan: proc() ").append(seedname).
                  append(" span has timed out ").append(((Span) sp1).toStringBuilder(null)));
        }
        long n = ((Span) sp1).sendToRTMS();
        if (n > nextExpected) {
          nextExpected = n;
        }
        spans.remove((Span) sp1); // take it off the linked list
      }
      // If the span has lots of blocks, put it out.
      if (((Span) sp1).getNsamp() > NSAMP_FORCEOUT) {
        if (dbg) {
          parent.prt(Util.clear(tmpsb).append("OORChan: proc() ").append(seedname).
                  append(" span has enough data ").append(((Span) sp1).toStringBuilder(null)));
        }
        long n = ((Span) sp1).sendToRTMS();
        if (n > nextExpected) {
          nextExpected = n;
        }
        spans.remove((Span) sp1); // take it off the linked list
      }
    }
    //parent.prt("OORChan end process for "+toString());
  }

  public synchronized void forceOut() {
    Object[] sp = spans.toArray();
    for (Object sp1 : sp) {
      parent.prt(Util.clear(tmpsb).append("OORChan: proc() ").append(seedname).
              append(" span forced out ").append(((Span) sp1).toStringBuilder(null)));
      ((Span) sp1).forceOut();
      spans.remove((Span) sp1); // take it off the linked list
    }
  }

  /**
   * this internal class represents a group of contiguous RTMSBufs. It tracks the list of buffers
   * (in time order), the start and end time, and last updated clock time.
   */
  class Span {

    long firstTime;
    long lastTime;
    long lastUpdate;
    LinkedList<RTMSBuf> span;
    double rate;
    int nsamp;
    StringBuilder tmpsb = new StringBuilder(100);
    StringBuilder seedname = new StringBuilder(12);

    /**
     * return the first time as a millisecond offset
     *
     * @return The milliseconds offset (GregorianCalendar form)
     */
    public long getFirstTime() {
      return firstTime;
    }

    /**
     * return the last time as a millisecond offset
     *
     * @return The milliseconds offset (GregorianCalendar form)
     */
    public long getLastTime() {
      return lastTime;
    }

    /**
     * return the last updated time as a millisecond offset
     *
     * @return The milliseconds offset (GregorianCalendar form)
     */
    public long getLastUpdate() {
      return lastUpdate;
    }

    /**
     * return total # of samples represented by this span
     *
     * @return The number of samples in the span
     */
    public int getNsamp() {
      return nsamp;
    }

    /**
     * get the linked list of blocks *
     *
     */
    public LinkedList<RTMSBuf> getSpanLinkedList() {
      return span;
    }

    /**
     * return a string representation of this span object
     *
     * @return String rep of this span (start time, end time, nsamps and age
     */
    @Override
    public String toString() {
      return toStringBuilder(null).toString();
    }

    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if (tmp == null) {
        sb = Util.clear(tmpsb);
      }
      synchronized (sb) {
        sb.append("Span =").append(Util.ascdatetime2(firstTime)).append(" to ").
                append(Util.ascdatetime2(lastTime)).append(" ns=").append(nsamp).
                append(" age=").append(System.currentTimeMillis() - lastUpdate).
                append(" RTMSBsize=").append(span.size());
      }
      return sb;
    }

    /**
     * create a new span with the first RTMSBuf give
     *
     * @param b The RTSMBuf that constitutes this span initially!
     */
    public Span(RTMSBuf b2) {
      span = new LinkedList<>();
      RTMSBuf b = RTMSBuf.getFreeBuf();
      b.setRTMSBuf(b2);
      span.add(b);
      Util.clear(seedname).append(b.getSeedname());
      rate = b.getRate();
      firstTime = b.getStartTime();
      lastTime = b.getNextTime();
      lastUpdate = System.currentTimeMillis();
      nsamp = b.getNsamp();
      if (dbg) {
        parent.prta(Util.clear(tmpsb).append("OORSpan: ").append(seedname).append(" Create a span at ").append(Util.asctime(b.getStartTime())));
      }
    }

    /**
     * return size of span in buf
     *
     * @return the # of RTMSBus in the span
     */
    public int size() {
      return span.size();
    }

    /**
     * return th ith RTMSBuf of the span
     *
     * @return The ith RTMSBuf
     */
    public RTMSBuf get(int index) {
      return (RTMSBuf) span.get(index);
    }

    /**
     * send this span to the RawToMiniSeed static object
     *
     * @return the milliseconds offset of the next sample after this span
     */
    public synchronized long sendToRTMS() {
      if (dbg) {
        Util.clear(tmpsb).append("OORSpan: ").append(seedname).append(" sendToRTMS() ");
        toStringBuilder(tmpsb);
        parent.prta(tmpsb);
      }
      for (RTMSBuf span1 : span) {
        ((RTMSBuf) span1).sendToRTMS();
      }
      // This if is new, we were getting index errors on span.get(span.size()-1) so the span was empty
      long expect = 0;
      if (span.size() > 0) {
        expect = span.get(span.size() - 1).getNextTime();
        if (expect < 110000000) {
          parent.prta("OORSpan: **** Span.sendToRTMS() expected low=" + expect);
        }
      }
      for (RTMSBuf span1 : span) {
        RTMSBuf.freeBuf(span1); // release all space in bufs
      }
      span.clear();         // remove all items from this span - they have been sent.
      return expect;
    }

    /**
     * send this span to the RawToMiniSeed static object
     *
     * @return the milliseconds offset of the next sample after this span
     */
    public synchronized void forceOut() {
      if (dbg) {
        Util.clear(tmpsb).append("OORSpan: ").append(seedname).append(" forceout() ");
        toStringBuilder(tmpsb);
        parent.prta(tmpsb);
      }
      for (int i = 0; i < span.size(); i++) {
        if (dbg) {
          Util.clear(tmpsb).append("OORSpan: send span block ").append(i).append(" ");
          span.get(i).toStringBuilder(tmpsb);
          parent.prta(tmpsb);
        }
        span.get(i).sendToRTMS();

      }
      if (dbg) {
        parent.prta(Util.clear(tmpsb).append("OORSpan: ").append(seedname).append(" do RTMS forceout() on ").append(seedname));
      }
      RawToMiniSeed.forceout(seedname);
      for (RTMSBuf span1 : span) {
        RTMSBuf.freeBuf(span1); // release all space in bufs
      }
      span.clear();         // remove all items from this span - they have been sent.
      span = null;
    }

    /**
     * attempt to add b to the beginning or end of this span, return true if successful
     *
     * @param b The buffer to try to add
     * @return true if the buffer is at beginning or end, false otherwise
     */
    public synchronized boolean add(RTMSBuf b) {
      if (Math.abs(b.getStartTime() - lastTime) < .5 / rate * 1000) {
        if (dbg) {
          Util.clear(tmpsb).append("OORSpan: ").append(b.getSeedname()).append(" Add end time=").append(Util.asctime(b.getStartTime())).append(" to ");
          toStringBuilder(tmpsb);
          parent.prta(tmpsb);
        }
        RTMSBuf b2 = RTMSBuf.getFreeBuf();
        b2.setRTMSBuf(b);
        span.addLast(b2);
        lastTime = b.getNextTime();
        nsamp += b.getNsamp();
        lastUpdate = System.currentTimeMillis();
        return true;
      }
      if (Math.abs(b.getNextTime() - firstTime) < .5 / rate * 1000) {
        if (dbg) {
          Util.clear(tmpsb).append("OORSpan: ").append(b.getSeedname()).
                  append("Add beg time=").append(Util.asctime(b.getStartTime())).append(" to ");
          toStringBuilder(tmpsb);
          parent.prta(tmpsb);
        }
        RTMSBuf b2 = RTMSBuf.getFreeBuf();
        b2.setRTMSBuf(b);
        span.addFirst(b2);
        firstTime = b.getStartTime();
        nsamp += b.getNsamp();
        lastUpdate = System.currentTimeMillis();

        return true;
      }
      if (firstTime <= b.getStartTime() && lastTime >= b.getNextTime()) {  // buffer is a duplicate of other data
        if (dbg) {
          Util.clear(tmpsb).append("OORSpan: ").append(b.getSeedname()).
                  append(" Dup block=").append(Util.asctime(b.getStartTime())).append(" within ");
          toStringBuilder(tmpsb);
          parent.prta(tmpsb);
        }
        return true;    // This effectively discards the block
      }
      return false;
    }

    /**
     * see if this span can be concatenated with the given. IF so update this span to rep both
     *
     * @param Another span to see if its contiguous
     * @return True if the two spans combined (the passed span is then not needed)
     */
    public synchronized boolean combine(Span s) {
      if (Math.abs(firstTime - s.getLastTime()) < .5 / rate * 1000) {
        //if(dbg)
        {
          Util.clear(tmpsb).append("OORSpan: ").append(seedname).append(" Combine beg s=");
          toStringBuilder(tmpsb);
          tmpsb.append(" with ").append(s.toStringBuilder(null));
          parent.prta(tmpsb);
        }
        for (int i = s.size() - 1; i >= 0; i--) {
          span.addFirst(s.get(i)); // add in reverse to begin
        }
        firstTime = s.getFirstTime();             // FIrst time should be from s
        nsamp += s.getNsamp();
        lastUpdate = Math.max(lastUpdate, s.getLastUpdate());//Which ever is newer
        return true;
      }
      if (Math.abs(lastTime - s.getFirstTime()) < .5 / rate * 1000) {
        //if(dbg)
        {
          Util.clear(tmpsb).append("OORSpan: ").append(seedname).append("Combine end s=");
          toStringBuilder(tmpsb);
          tmpsb.append(" with ").append(s.toStringBuilder(null));
          parent.prta(tmpsb);
        }
        for (int i = 0; i < s.size(); i++) {
          span.add(s.get(i));// Add s to end of spans
        }
        lastTime = s.getLastTime();               // last should now come from s
        nsamp += s.getNsamp();
        lastUpdate = Math.max(lastUpdate, s.getLastUpdate());//Which ever is newer
        return true;
      }
      // If the block is entirely within another block, zap it
      if (firstTime <= s.getFirstTime() && lastTime >= s.getLastTime()) {  // s is entirely inside and can be deleted
        Util.clear(tmpsb).append("OORSpan: ").append(seedname).append(" ** Combine overlap s=");
        toStringBuilder(tmpsb);
        tmpsb.append(" with ").append(s.toStringBuilder(null)).append(" free the blocks");
        for (int i = span.size() - 1; i >= 0; i--) {
          RTMSBuf.freeBuf(span.get(i)); // Free the blocks consumed
        }
        return true;
      }
      return false;
    }
  }

  /**
   * OORChanServer.java - This server looks for a telnet type connection,accepts a message of the
   * form yyyy,doy,node\n and forms up a FORCELOADIT! block for the EdgeBlockQueue. This triggers
   * the opening of the file in Replicator and causes the file to be checked.
   *
   *
   * @author davidketchum
   */
  public final class OORChanServer extends Thread {

    int port;
    ServerSocket d;
    int totmsgs;
    boolean terminate;
    boolean running;
    String tag;

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
     * Creates a new instance of OORChanServers
     *
     * @param porti The port that will be used for this service normally 7960
     * AnssPorts.EDGEMOM_FORCE_CHECK_PORT
     */
    public OORChanServer(int porti) {
      port = porti;
      terminate = false;
      tag = "OORCSrv: ";
      // Register our shutdown thread with the Runtime system.
      Runtime.getRuntime().addShutdownHook(new ShutdownOORChanServer(this));
      running = true;
      gov.usgs.anss.util.Util.prta("new Thread " + getName() + " " + getClass().getSimpleName());
      start();
    }

    @Override
    public String toString() {
      return "OORCsrv: " + port + " d=" + d + " port=" + port + " totmsgs=" + totmsgs;
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
          parent.prta(tag + " OORCsrv: " + port + " Open Port=" + port);
          d = new ServerSocket(port);
          break;
        } catch (SocketException e) {
          if (e.getMessage().contains("Address already in use")) {
            parent.prt(tag + " OORCsrv: " + port + " Address in use - exit ");
            port++;
          } else {
            parent.prt(tag + " OORCsrv: " + port + " Error opening TCP listen port =" + port + "-" + e.getMessage());
            try {
              Thread.sleep(2000);
            } catch (InterruptedException E) {
            }
          }
        } catch (IOException e) {
          parent.prt(tag + " OORCsrv: " + port + "ERror opening socket server=" + e.getMessage());
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
          parent.prta(tag + " OORCsrv: " + port + " at accept");
          Socket s = d.accept();
          parent.prta(tag + " OORCsrv: " + port + " from " + s);
          try {
            OutputStream out = s.getOutputStream();
            out.write("Enter a Seedname to dump (*=all chans, %=show buffer detail)\n".getBytes());

            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
              if (line.length() < 1) {
                break;
              }
              boolean detail = false;
              if (line.contains("%")) {
                detail = true;
                line = line.replaceAll("%", "");
              }
              if (line.substring(0, 1).equals("*")) {
                int totSpans = 0;
                int noor = 0;
                Object[] oors = chans.values();
                for (int i = 0; i < oors.length; i++) {
                  if (oors[i] != null) {
                    noor++;
                    totSpans += ((OORChan) oors[i]).getNSpans();
                    if (detail) {
                      out.write((i + " " + ((OORChan) oors[i]).toStringBufDetail()).getBytes());
                    } else {
                      out.write((i + " " + ((OORChan) oors[i]).toString()).getBytes());
                    }
                  }

                }
                out.write(("#OOR=" + noor + " #spans=" + totSpans + "\n").getBytes());
                out.write((RTMSBuf.getStatus() + "\n").getBytes());
                continue;
              }
              OORChan oor = chans.get(Util.getHashFromSeedname(line));
              StringBuilder tmp;
              if (oor != null) {
                if (detail) {
                  tmp = oor.toStringBufDetail();
                } else {
                  tmp = oor.toStringBuilder(null);
                }
                for (int i = 0; i < tmp.length(); i++) {
                  out.write((byte) tmp.charAt(i));
                }
              } else {
                out.write("Seedname not found\n".getBytes());
              }
            }
          } catch (IOException e) {
            Util.SocketIOErrorPrint(e, "OORCsrv:" + port + " IOError on socket");
          } catch (RuntimeException e) {
            parent.prt(tag + " OORCsrv:" + port + " RuntimeException in EBC FChk e=" + e + " " + (e == null ? "" : e.getMessage()));
            if (e != null) {
              e.printStackTrace();
            }
          }
          parent.prta(tag + " OORCsrv:" + port + " ForceCheckHandler has exit on s=" + s);
          if (s != null) {
            if (!s.isClosed()) {
              try {
                s.close();
              } catch (IOException e) {
                parent.prta(tag + " OORCsrv:" + port + " IOError closing socket");
              }
            }
          }
        } catch (IOException e) {
          if (!terminate) {
            Util.SocketIOErrorPrint(e, tag + " OORCsrv: accept loop port=" + port);
          } else {
            break;
          }
          if (e.getMessage().contains("operation interrupt")) {
            parent.prt(tag + " OORCsrv:" + port + " interrupted.  continue terminate=" + terminate);
            continue;
          }
          if (terminate) {
            break;
          }
        }
      }       // end of infinite loop (while(true))
      //parent.prt("Exiting OORChanServers run()!! should never happen!****\n");
      parent.prt(tag + " OORCsrv: " + port + " read loop terminated");
      running = false;
    }

    private class ShutdownOORChanServer extends Thread {

      OORChanServer thr;

      public ShutdownOORChanServer(OORChanServer t) {
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
        parent.prta(tag + " OORCsrv: " + port + "Shutdown Done.");
      }
    }
  }

  public static void main(String[] args) {
    String file = "RIS.out";
    EdgeProperties.init();
    Util.debug(false);

    Util.setModeGMT();
    EdgeBlockQueue ebq = new EdgeBlockQueue(1000);
    Util.prt("testing 123");
    Util.setNoconsole(true);
    Util.prt("testin 456");
    EdgeThreadTemplate et = new EdgeThreadTemplate("-empty", "tag");
    RTMSBuf.init(et);
    StringBuilder chan = new StringBuilder(12);
    Socket s = null;
    //try {
    OORChan serv = new OORChan(Util.clear(chan).append("USDUG  BHZ  "), et);
    while (true) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }
    }
    //}
    //catch(IOException e) {Util.prt("IOExp="+e.getMessage());}
    //RawInputSocket sock = new RawInputSocket(s, true, 60000 );
    //sock.setReadraw(file);

  }
}
