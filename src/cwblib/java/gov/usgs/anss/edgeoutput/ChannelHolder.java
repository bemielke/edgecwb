/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import java.util.ArrayList;
import java.lang.reflect.*;
import java.util.ConcurrentModificationException;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edgemom.Hydra;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import java.util.GregorianCalendar;

/** This class is used to store/buffer input data that might be out of order.  The Hydra system subscribes
 * to these so that when data is ready to be sent out, it can be pushed to the subscribed clients.  
 * The ChannelInfrastructures is the user of this class and provides most of the interface to data stored in 
 * this class.
 *
 * @author davidketchum
 */
public final class ChannelHolder {

  private static final TLongObjectHashMap<ChannelHolder> channels = new TLongObjectHashMap<>();
  //private static final Map<String, ChannelHolder> channels = (Map<String, ChannelHolder>)
  //      Collections.synchronizedMap(new TreeMap<String, ChannelHolder>());
  private StringBuilder seedname = new StringBuilder(12);
  private static StringBuilder dbgSeedname = new StringBuilder(12);
  private int maxDepth;         // in seconds
  private int minRecords;
  private TimeSeriesBlock[] msrecs;
  private int msize;        // The number of msrecs which have been allocated storage
  private int nused;        // Number of the msrecs currently in use
  private int inorderRejects;
  private ArrayList<ChannelInfrastructureSubscriber> subscribers;
  private Class defaultSubscriber;
  private Constructor defaultConstructor;
  private Class bufferClass;
  private double rate;        // digitizing rate in hz
  private int msover2;        // the number of ms in 1/2 sample width
  private boolean dbg;
  private boolean gapdbg;
  private static EdgeThread par;
  private StringBuilder tmpsb = new StringBuilder(100);

  public static void setDebugSeedname(String s) {
    dbgSeedname = Util.clear(dbgSeedname).append(s.replaceAll("_", " "));
    Util.prt("DebugSeedname=" + dbgSeedname);
  }

  public static String getDebugSeedname() {
    return dbgSeedname.toString();
  }

  public ChannelInfrastructureSubscriber getSubscriber(int i) {
    if (subscribers.size() <= i) {
      return null;
    }
    return subscribers.get(i);
  }
  private static StringBuilder sb = new StringBuilder(1000);

  public static synchronized StringBuilder getSummary() {
    Util.clear(sb);
    if (channels == null) {
      return sb.append("Channel Holder Summary #chans=0\n");
    }
    int sum = 0;
    sb.append(Util.asctime()).append(" Channel Holder Summary #chans=").append(channels.size()).append("\n");
    try {
      TLongObjectIterator<ChannelHolder> itr = channels.iterator();
      int i = 0;
      while (itr.hasNext()) {
        itr.advance();
        ChannelHolder obj = itr.value();
        sb.append(i).append(" ").append(obj.toString()).append(" ").append(obj.getSubscriber(0)).append("\n");
        sum += obj.getAllocation();
        i++;
      }
    } catch (ConcurrentModificationException e) {
      par.prt("ConcurrentModificationException in getSummary() in ChannelHoldings");
    }
    sb.append(" Total allocation=").append(sum / 1000000).append(" MB");
    return sb;
  }

  public static synchronized String getDetail() {
    Util.clear(sb);
    if (channels == null) {
      return "Channel Holder Summary #chans=0\n";
    }
    sb.append("Channel Holder Summary #chans=").append(channels.size()).append("\n");
    TLongObjectIterator<ChannelHolder> itr = channels.iterator();
    int i = 0;
    int sum = 0;
    while (itr.hasNext()) {
      itr.advance();
      ChannelHolder obj = itr.value();
      sb.append(i).append(" ").append(obj.toString()).append("\n");
      sum += obj.getAllocation();
      for (int j = 0; j < obj.getSize(); j++) {
        sb.append(obj.get(j)).append("\n");
      }
      i++;
    }
    sb.append("Total allocation=").append(sum).append(" bytes");
    return sb.toString();
  }

  public int getAllocation() {
    int sum = 0;
    for (int i = 0; i < msize; i++) {
      if (msrecs[i] != null) {
        sum += msrecs[i].getBuf().length;
      }
    }
    return sum;
  }

  public TimeSeriesBlock get(int i) {
    return msrecs[i];
  }

  public int getSize() {
    return msize;
  }

  /**
   * Creates a new instance of ChannelHolder
   *
   * @param inbuf The time series block to use to model this holder for seedname, etc
   * @param secDepth the depth in seconds for the list of blocks
   * @param minRecs The minimum number of records to hold in the list of recent data
   * @param defSub the default subscriber class to use to create new instances of the output portion
   * @param defWaitSec How long to wait for the next data before timing out and moving on
   */
  public ChannelHolder(TimeSeriesBlock inbuf, int secDepth, int minRecs, Class defSub, int defWaitSec) {
    dbg = false;
    maxDepth = secDepth;
    if (par == null) {
      par = ChannelInfrastructure.getParent();
    }
    minRecords = minRecs;
    defaultSubscriber = defSub;
    msrecs = new TimeSeriesBlock[Math.min(20, minRecs)];
    msize = 0;
    nused = 0;
    Util.clear(seedname).append(inbuf.getSeedNameSB());
    //seedname = inbuf.getSeedNameString();
    if (seedname.substring(0, 5).equals("USTST")) {
      dbg = true;
    }
    if (dbgSeedname.length() > 1) {
      dbg = true;
      for (int i = 0; i < dbgSeedname.length(); i++) {
        if (seedname.charAt(i) != dbgSeedname.charAt(i)) {
          dbg = false;
          break;
        }
      }
    }
    rate = inbuf.getRate();
    if (rate > 0) {
      msover2 = (int) (500. / rate);
    } else {
      msover2 = 10;
    }
    subscribers = new ArrayList<>(1);
    try {
      Constructor[] cons = defaultSubscriber.getConstructors();
      for (Constructor con : cons) {
        Class[] types = con.getParameterTypes();
        if (types.length == 4) {
          if (types[0].getName().equals("java.lang.String")
                  && types[1].getName().equals("int") && types[2].getName().equals("long") && types[3].getName().equals("double")) {
            defaultConstructor = con;
          }
        }
      }
      if (defaultConstructor != null) {

        try {
          Object[] args = new Object[4];
          args[0] = seedname.toString();
          args[1] = defWaitSec;
          args[2] = inbuf.getTimeInMillis();
          args[3] = inbuf.getRate();
          ChannelInfrastructureSubscriber sub = (ChannelInfrastructureSubscriber) defaultConstructor.newInstance(args);
          subscribers.add(sub);
        } catch (InstantiationException e) {
          par.prta(Util.clear(tmpsb).append("Instantiation Exception creating a new ").append(defaultSubscriber.getName()).
                  append(" ").append(e.getMessage()).append(" seedname=").append(seedname));
          e.printStackTrace(par.getPrintStream());
        } catch (IllegalAccessException e) {
          par.prta(Util.clear(tmpsb).append("Illegal Access Exception creating a new ").
                  append(defaultSubscriber.getName()).append(" ").append(e.getMessage()));
          e.printStackTrace(par.getPrintStream());
        } catch (InvocationTargetException e) {
          par.prta(Util.clear(tmpsb).append("InvocationTarget Exception creating a new ").
                  append(defaultSubscriber.getName()).append(" ").append(e.getMessage()));
          e.printStackTrace(par.getPrintStream());
        }
      }

    } catch (SecurityException | IllegalArgumentException e) {
      par.prta(Util.clear(tmpsb).append("Caught exception e=").append(e == null ? "null" : e.getMessage()));
      if (e != null) {
        e.printStackTrace();
      }

    }
    //if(dbg)
    Util.prt("CH: new2 " + seedname + " dbg=" + dbg + " dbgseed=" + dbgSeedname);
    addData(inbuf);
    channels.put(Util.getHashFromSeedname(seedname), (ChannelHolder) this);     // Add to list of all channel holders
  }

  public ChannelHolder(StringBuilder seed, int secDepth, int minRecs) {
    dbg = false;
    Util.clear(seedname).append(seed);
    if (seedname.substring(0, 6).equals("USTST")) {
      dbg = true;
    }
    if (dbgSeedname.length() > 1) {
      dbg = true;
      for (int i = 0; i < dbgSeedname.length(); i++) {
        if (seedname.charAt(i) != dbgSeedname.charAt(i)) {
          dbg = false;
          break;
        }
      }
    }
    maxDepth = secDepth;
    if (par == null) {
      par = ChannelInfrastructure.getParent();
    }
    minRecords = minRecs;
    msrecs = new TimeSeriesBlock[Math.min(20, minRecs)];
    msize = 0;
    nused = 0;
    rate = 0.;
    if (rate > 0) {
      msover2 = (int) (500. / rate);
    } else {
      msover2 = 10;
    }
    channels.put(Util.getHashFromSeedname(seedname), (ChannelHolder) this);     // Add to list of all channel holders
    //if(dbg)
    Util.prt(Util.clear(tmpsb).append("CH: new ").append(seedname).append(" dbg=").append(dbg).append(" dbgseed=").append(dbgSeedname));
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sbt = tmp;
    if (sbt == null) {
      sbt = Util.clear(tmpsb);
    }
    synchronized (sbt) {
      sbt.append("CH:").append(seedname).append(" dpth=").append(maxDepth).append(" mnRc=").append(minRecords).
              append(" sz=").append(msize).append(" used=").append(nused).append(" sz(B)=").append(getAllocation()).
              append(" ms=").append(Util.asctime2(msrecs[0].getTimeInMillis(), null)).append(" ").
              append(msrecs[nused - 1].getTimeInMillis() - msrecs[0].getTimeInMillis()).append(" ms dbg=").append(dbg).
              append(" INOrej=").append(inorderRejects);
    }
    return sbt;
  }

  /**
   * this routine adds a bit of timeseries to this channels applying the maxsec rules and sends any
   * data to the subscribers.
   * <br>1) If the block is maxsec old than current newest block it is discarded.
   * <br>2) If the list of blocks is null, then it is created along with the default Subscriber
   * <br>3) If # used blocks on list = list size, a new block is created.
   * <br>4) The block is loaded() onto the next cleared block.
   * <br>5) The blocks are sorted into order
   * <br>6) If the newest block on the list is older than the oldest by maxsec, the list is purged
   * by clearing any blocks which are now too old.
   * <br>7) for each subscriber to this ChannelHolder we attempt to get the next desired block for
   * that subscriber and waittime. If any are found they are sent to the subscriber. (Desired is the
   * time next wanted, waittime is how long the wait will be relative to the 'newest' block in list.
   * When the older blocks are newer by more than waittime, they are forwarded. )
   *
   * @param inbuf The timeseries buffer to add to this channel
   */

  public final synchronized void addData(TimeSeriesBlock inbuf) {
    if (bufferClass == null) {
      bufferClass = inbuf.getClass();
    } else if (!bufferClass.isInstance(inbuf)) {
      par.prt(Util.clear(tmpsb).append(seedname).append(" ******* Expected class is ").append(bufferClass.getName()).
              append(" addData() passed an ").append(inbuf.getClass().getName()).append(" discard"));
      RuntimeException e = new RuntimeException("Classes different in addData() for " + seedname + " "
              + bufferClass.getSimpleName() + " vs " + inbuf.getClass().getSimpleName());
      par.prta(Util.clear(tmpsb).append("Bad data =").append(inbuf.toStringBuilder(null)));
      par.prta(e.getMessage());
      e.printStackTrace(par.getPrintStream());
      return;
    }
    if (!Util.stringBuilderEqual(inbuf.getSeedNameSB(), seedname)) {
      par.prt(Util.clear(tmpsb).append("wrong seedname to ChannelHolder = ").append(seedname).
              append(" expected. is=").append(inbuf.getSeedNameSB()));
      return;      // For wrong channel!
    }

    if (rate <= 0.) {
      rate = inbuf.getRate();
    }
    // if this data is too old, do not even consider it!
    if (msize > 0) {
      if (msrecs[nused - 1].getTimeInMillis() - inbuf.getTimeInMillis() > maxDepth * 1000
              && msize >= minRecords) {
        //par.prt("Late data oor "+inbuf);
        for (ChannelInfrastructureSubscriber sub : subscribers) {
          //par.prt(i+" Late data oor sub="+sub.getClass().getName());
          if (sub.getClass().getName().contains("Hydra")) {
            sub.queue(inbuf, 1);
          }
        }
        return;
      }
    }

    // Add this to end of list
    try {
      // we always want the array to be bigger than the number of used elements (even if we add one!)
      if (msize + 1 >= msrecs.length) {    // Can we put another record in this array, if not expand it
        TimeSeriesBlock[] tmp = new TimeSeriesBlock[msrecs.length * 2];
        System.arraycopy(msrecs, 0, tmp, 0, msrecs.length);    // move all objects to temp
        msrecs = tmp;                   // point ms recs at its larger copy of itself!
        if (dbg) {
          par.prt(Util.clear(tmpsb).append("CH: ").append(seedname).append(" increase array size to ").
                  append(msrecs.length).append(" nused=").append(nused).append(" msize=").append(msize));
        }
      }

      // We need to get a new TimeSeriesBlock to match the subtype of inbuf, use reflection to build it
      if (nused >= msize) {
        Class objClass = inbuf.getClass();
        byte[] bb = new byte[10];
        Class[] em = new Class[1];
        em[0] = bb.getClass();     // An array of bytes
        try {
          Constructor bufferConstructor = objClass.getConstructor(em);
          Object[] args = new Object[1];
          args[0] = inbuf.getBuf();
          TimeSeriesBlock ps = (TimeSeriesBlock) bufferConstructor.newInstance(args);
          msrecs[nused] = ps;
          msize++;
          if (dbg) {
            par.prta(Util.clear(tmpsb).append("CH: addData() ").append(seedname).append(" New ").
                    append(ps.getClass().getSimpleName()).append(" at ").append(nused).append(" msize=").append(msize).
                    append(" ").append(ps.toString().substring(0, 66)));
          }
        } catch (NoSuchMethodException e) {
          par.prta(Util.clear(tmpsb).append("Cannot find buffer constructor for ").
                  append(inbuf.getClass().getSimpleName()).append(" ").append(e.getMessage()));
        } catch (InstantiationException e) {
          par.prta(Util.clear(tmpsb).append("Instantiation Exception creating a new ").
                  append(inbuf.getClass().getSimpleName()).append(" ").append(e.getMessage()));
        } catch (IllegalAccessException e) {
          par.prta(Util.clear(tmpsb).append("Illegal Access Exception creating a new ").
                  append(inbuf.getClass().getSimpleName()).append(" ").append(e.getMessage()));
        } catch (InvocationTargetException e) {
          par.prta(Util.clear(tmpsb).append("InvocationTarget Exception creating a new ").
                  append(inbuf.getClass().getSimpleName()).append(" ").append(e.getMessage()));
        }
      } else {
        msrecs[nused].load(inbuf.getBuf());   // load it at end
        if (dbg) {
          par.prta(Util.clear(tmpsb).append("CH: addData() ").append(seedname).append(" load ").
                  append(msrecs[nused].getClass().getSimpleName()).append(" at ").append(nused).
                  append(" ").append(msrecs[nused].toStringBuilder(null)));
        }
      }
      if (nused > 0) {
        if (msrecs[nused].getTimeInMillis() < msrecs[nused - 1].getTimeInMillis()) {    // it belongs somewhere earlier
          int i;
          for (i = 0; i < nused - 1; i++) {
            if (msrecs[i].getTimeInMillis() > msrecs[nused].getTimeInMillis()) {
              break;
            }
          }
          TimeSeriesBlock tmp = msrecs[msize - 1];      // this is the one about to be shifted out of the list
          if (dbg) {
            par.prt(Util.clear(tmpsb).append("CH: ").append(seedname).append(" insert at ").append(i).
                    append(" nused=").append(nused).append(" msize=").append(msize));
          }
          //if(msize > msrecs.length-1) par.prt("CH: insert "+seedname+" at i="+i+" nused="+nused+
          //    " msize="+msize+" len="+msrecs.length+" is OOB");
          for (int j = msize - 1; j >= i; j--) {
            msrecs[j + 1] = msrecs[j];// Make a hole at i shifting stuff down one
          }
          msrecs[i] = msrecs[nused + 1];
          msrecs[msize] = null;
          if (nused + 1 < msize) {
            msrecs[nused + 1] = tmp;    // put the spare back where it now belongs (if fails tmp is same as msrecs[nused+1]
          }
        }
      }
      nused++;

      // test that all appears well
      if (nused > msize) {
        par.prt(Util.clear(tmpsb).append("CH : ### ").append(seedname).
                append(" nused > msize~! nused=").append(nused).append(" msize=").append(msize));
      }
      for (int i = 0; i < msize; i++) {
        if (msrecs[i] == null) {
          par.prt(Util.clear(tmpsb).append("CH: ### ").append(seedname).append(" has null at i=").append(i).
                  append(" msize=").append(msize));
        }
      }
      for (int i = 0; i < nused - 1; i++) {
        if (msrecs[i].getTimeInMillis() > msrecs[i + 1].getTimeInMillis()) {
          par.prt(Util.clear(tmpsb).append("CH: ### ").append(seedname).
                  append(" out of order at i=").append(i).append(" nused=").append(nused));
        }
      }
    } catch (IllegalSeednameException e) {
      par.prta(Util.clear(tmpsb).append("Illegal seedname addData() in ChannelHolding is impossible2 ").
              append(seedname).append(" e=").append(e.getMessage()));
      e.printStackTrace(par.getPrintStream());
      return;
    }
    //Collections.sort(msrecs);   // make sure still in order!

    // Now trim this list to have no data older than ms
    //if(dbg) par.prt(seedname+"size="+msize+" nused="+nused);
    while ((msrecs[nused - 1].getTimeInMillis() - msrecs[0].getTimeInMillis()) / 1000 > maxDepth) {
      if (nused <= minRecords) {
        break;      // do not pare below the minimum
      }
      if (dbg) {
        par.prta(Util.clear(tmpsb).append("CH: addData() ").append(seedname).append(" nused=").append(nused).
                append(" size=").append(msize).append(" purge ").append(msrecs[0].toStringBuilder(null)));
      }
      msrecs[0].clear();
      TimeSeriesBlock tmp = msrecs[0];
      System.arraycopy(msrecs, 1, msrecs, 0, msize - 1);
      msrecs[msize - 1] = tmp;
      nused--;
      //Collections.sort(msrecs);
    }
    TimeSeriesBlock s;
    for (ChannelInfrastructureSubscriber sub : subscribers) {
      // forward all blocks which are now desired to subscriber
      gapdbg = false;
      if (sub.getAllowOverlaps()) {   // Not strictly incrineasing will tolerate overlaps
        while ((s = getNextData(sub.getDesired(), sub.getEarliest(), sub.getWaitTime())) != null) {
          if (sub.getClass().getName().contains("Hydra")) {   // screen Hydra bound with checkInorder()
            try {
              if (Hydra.checkInorder(s)) {
                sub.queue(s, 0);
              } //sub.queue(s.getBuf(), s.getBlockSize(), inbuf.getClass());
              else {
                inorderRejects++;
                sub.queue(s, 1);    // Send out of order data
              }
            } catch (RuntimeException e) { // If the packet is from the future reject it.
              if (e.getMessage().contains("Packet from future")) {
                throw e;
              }
              inorderRejects++;
            }
          } //else sub.queue(s.getBuf(), s.getBlockSize(), inbuf.getClass());// do not screen liss, etc.
          else {
            sub.queue(s, 0);
          }

          if (gapdbg) {
            par.prta(Util.clear(tmpsb).append("Send aft gap ").append(sub.toStringBuilder(null)).append(" ").append(s.toStringBuilder(null)));
          }
        }
      } else {    // If strictly increasing use this getNextData() (Hydra)
        while ((s = getNextData(sub.getDesired(), sub.getWaitTime())) != null) {
          if (sub.getClass().getName().contains("Hydra")) {    // screen Hydra bound with checkInorder()
            if (Hydra.checkInorder(s)) {
              sub.queue(s, 0);
            } //sub.queue(s.getBuf(), s.getBlockSize(), inbuf.getClass());
            else {
              inorderRejects++;
              sub.queue(s, 1);
            }
          } else {
            sub.queue(s, 0);
          }
          //else sub.queue(s.getBuf(), s.getBlockSize(), inbuf.getClass()); // do not screen liss, etc.
          if (gapdbg) {
            par.prta(Util.clear(tmpsb).append("Send aft gap ").append(sub.toStringBuilder(null)).
                    append(" ").append(s.toStringBuilder(null)));
          }
        }
      }
    }

  }

  public void shutdown() {
    par.prta("CH: for " + seedname + " is shuting down.");
    for (ChannelInfrastructureSubscriber subscriber : subscribers) {
      subscriber.shutdown();
    }

  }

  /**
   * given an desired time, return either the packet at this time, or if the max wait window has
   * expired, the next packet after that time
   *
   * @param desired The desired time, this will be updated to next desired time if a packet is
   * returned.
   * @param maxwait The max wait time for desired, if expired, return next best packet
   * @return The above packet or null if none matches the criteria, desired is updated to next time
   * if not null
   */
  /*public synchronized TimeSeriesBlock getNextData(GregorianCalendar desired, int maxwait) {
    getNextData(desired.getTimeInMillis(), maxwait);
  }*/
  private TimeSeriesBlock getNextData(GregorianCalendar desired, int maxwait) {
    long maxwaitMS = maxwait * 1000L;
    TimeSeriesBlock nextBest = null;
    boolean sendNextBest = false;       // flag to send oldest data
    for (int i = 0; i < nused; i++) {
      // if its exactly the next one return it and update desired
      long diff = msrecs[i].getTimeInMillis() - desired.getTimeInMillis();
      if (Math.abs(diff) < msover2) {
        desired.setTimeInMillis(msrecs[i].getNextExpectedTimeInMillis());  // update the time in desired
        return msrecs[i];
      }
      // Look for the oldest packet over the wait interval and after desired time
      if (diff > msover2 && nextBest == null) {
        nextBest = msrecs[i];  // is it after desired and the oldest?
      }
      if (diff > maxwaitMS) {
        sendNextBest = true;       // Has time expired (newest -desired > maxwait)
      }
    }

    // OKAY, the easy one is not here, now decide if maxwait means there is something ready to go
    if (sendNextBest) {
      //gapdbg=true;      // when this is uncommented we get much output as the gap is sent out!
      par.prta(Util.clear(tmpsb).append("    *** send expected gap ").append(seedname).
              append(" desired=").append(Util.ascdatetime2(desired, null)).
              append(" got=").append(Util.ascdatetime2(nextBest.getTimeInMillis(), null)).
              append(" df=").append(nextBest.getTimeInMillis() - desired.getTimeInMillis()).
              append(" max=").append(maxwaitMS));
      desired.setTimeInMillis(nextBest.getNextExpectedTimeInMillis());
      return nextBest;
    }
    return null;
  }

  /**
   * given an desired time and a earliest time, return either the packet at this time, or oldest
   * data between earliest and desired, or if the max wait window has expired, the next packet after
   * that time,
   *
   * @param desired The desired time, this will be updated to next desired time if a packet is
   * returned.
   * @param earliest a time in ms of the earliest data to get next
   * @param maxwait The max wait time for desired, if expired, return next best packet
   * @return The above packet or null if none matches the criteria, desired is updated to next time
   * if not null
   */
  /*public synchronized TimeSeriesBlock getNextData(GregorianCalendar desired, int maxwait) {
    getNextData(desired.getTimeInMillis(), maxwait);
  }*/
  private TimeSeriesBlock getNextData(GregorianCalendar desired, GregorianCalendar earliest, int maxwait) {
    long maxwaitMS = maxwait * 1000L;
    TimeSeriesBlock nextBest = null;
    boolean sendNextBest = false;       // flag to send oldest data
    TimeSeriesBlock nextOverlap = null;
    for (int i = 0; i < nused; i++) {
      // if its exactly the next one return it and update desired
      long diff = msrecs[i].getTimeInMillis() - desired.getTimeInMillis();
      if (Math.abs(diff) < msover2) {
        desired.setTimeInMillis(msrecs[i].getNextExpectedTimeInMillis());  // update the time in desired
        earliest.setTimeInMillis(msrecs[i].getTimeInMillis());              // updae time of last packet return
        return msrecs[i];
      }
      // Look for the oldest packet over the wait interval and after desired time
      if (diff > 7200000) {
        par.prt(Util.clear(tmpsb).append("***** got way future packet! df=").
                append(diff).append(" ").append(msrecs[i].toStringBuilder(null)));
        continue;
      }
      if (diff > msover2 && nextBest == null) {
        nextBest = msrecs[i];  // is it after desired and the oldest?
      }
      if (diff > maxwaitMS) {
        sendNextBest = true;       // Has time expired (newest -desired > maxwait)
      }
      // look for packets between earliest and desired and keep track of oldest one 
      if (msrecs[i].getTimeInMillis() > earliest.getTimeInMillis() && msrecs[i].getTimeInMillis() < desired.getTimeInMillis()) {
        if (nextOverlap == null) {
          nextOverlap = msrecs[i];
        } else if (msrecs[i].getTimeInMillis() < nextOverlap.getTimeInMillis()) {
          nextOverlap = msrecs[i];
        }
      }
    }
    // if there is a overlapping record send it
    if (nextOverlap != null) {
      par.prt(Util.clear(tmpsb).append("Send overlap packet ").append(seedname).append("ms=").append(nextOverlap.getTimeInMillis() - desired.getTimeInMillis()).append(" ").append(Util.asctime2(desired)));
      desired.setTimeInMillis(nextOverlap.getNextExpectedTimeInMillis());  // update the time in desired
      earliest.setTimeInMillis(nextOverlap.getTimeInMillis());              // updae time of last packet return
      return nextOverlap;
    }

    // OKAY, the easy one is not here, now decide if maxwait means there is something ready to go
    if (sendNextBest) {
      //gapdbg=true;      // when this is uncommented we get much output as the gap is sent out!
      par.prta(Util.clear(tmpsb).append("    *** send expected gap2 ").append(seedname).
              append(" desired=").append(Util.ascdatetime2(desired, null)).
              append(" got=").append(Util.asctime2(nextBest.getTimeInMillis(), null)).
              append(" df=").append(nextBest.getTimeInMillis() - desired.getTimeInMillis()).
              append(" max=").append(maxwaitMS));
      return nextBest;
    }
    return null;
  }

  /**
   * add any MiniSeed buffer to the current list of ChannelHolders
   *
   * @param buf A buffer of bytes of MiniSeed data in raw form
   * @param secDepth The depth in seconds of this ChannelHolder
   * @param minRecs The minimum number of records to hold (regardless of secDepth
   */
  public static void addMSData(TimeSeriesBlock buf, int secDepth, int minRecs) {
    par.prt("This is unsupported!!!!!!");
    ChannelHolder h = channels.get(Util.getHashFromSeedname(buf.getSeedNameSB()));
    if (h == null) {
      h = new ChannelHolder(buf, secDepth, minRecs, null, 240);
    } else {
      h.addData(buf);
    }
  }
}
