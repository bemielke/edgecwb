/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.alarm.SendEvent;
import java.util.GregorianCalendar;

/**
 * This class is a ChannelInfrastructureSubscriber which forwards data to LISSStationServer based on
 * its names.
 *
 * An interesting bit is that this class is generally created as a default subscriber to
 * ChannelHolder. Hence it is configured by a static method call to "setCommandLine()" which setup
 * which allows a configuration to be done once before the ChannelInfrastructure is started an any
 * object of this type are created.
 *
 * @author davidketchum
 */
public final class LISSOutputer extends ChannelInfrastructureSubscriber {

  GregorianCalendar desired = new GregorianCalendar();       // next desired time
  GregorianCalendar earliest = new GregorianCalendar();     // the time of the last packet we got
  String seedname;
  int waitTime;
  int npacket;
  LISSStationServer server;

  @Override
  public GregorianCalendar getEarliest() {
    return earliest;
  }

  @Override
  public GregorianCalendar getDesired() {
    return desired;
  }

  @Override
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    return tmp;
  }
  // This static area is for all of the queues

  static boolean dbg;
  static EdgeThread par;    // the parent edge thread for output purposes.

  @Override
  public void shutdown() {
    par.prta("LISSO: for " + seedname + " is shutingdown");
  }

  /**
   * Creates a new instance of LISSOutputer
   *
   * @param seed A 12 character seed name
   * @param wait The length of time this outputer is willing to wait for complete data
   * @param startTimeMS A Millisecond time (like for GregorianCalendar) of the start time desired.
   */
  public LISSOutputer(String seed, int wait, long startTimeMS) {
    seedname = seed;
    waitTime = wait;
    desired.setTimeInMillis(startTimeMS);
    earliest.setTimeInMillis(startTimeMS);
    server = LISSStationServer.getServer(seedname);
    if (par == null) {
      par = ChannelInfrastructure.getParent();
    }
  }

  /**
   * Creates a new instance of LISSOutputer
   *
   * @param seed A 12 character seed name
   * @param wait The length of time this outputer is willing to wait for complete data
   *
   * @param startTimeMS A Millisecond time (like for GregorianCalendar) of the start time desired.
   * @param rt The data rate of the channel - not currently used by LISSOutput but needed in
   * HydraOutput
   */
  public LISSOutputer(String seed, int wait, long startTimeMS, double rt) {
    seedname = seed;
    waitTime = wait;
    desired.setTimeInMillis(startTimeMS);
    earliest.setTimeInMillis(startTimeMS);
    server = LISSStationServer.getServer(seedname);
    if (par == null) {
      par = ChannelInfrastructure.getParent();
    }

  }

  /**
   * liss does not do strictly increasing, allow overlaps. Always return true
   *
   * @return always true
   */
  @Override
  public boolean getAllowOverlaps() {
    return true;
  }

  /**
   * get as string representing this LISSOutputer
   *
   * @return The representative string
   */
  @Override
  public String toString() {
    return "LISSO:" + seedname + " wt=" + waitTime + " "
            + Util.ascdate(earliest).substring(5) + " " + Util.asctime2(earliest)
            + Util.ascdate(desired).substring(5) + " " + Util.asctime2(desired) + " #pkt=" + npacket;
  }

  /**
   * I do not think this is used in the final design
   */
  @Override
  public void tickle() {
  }

  /**
   * return the wait time for this in seconds
   *
   * @return The waittime (time allowed to wait for in order data) in seconds
   */
  @Override
  public int getWaitTime() {
    return waitTime;
  }

  /**
   * this is the general call to put data in this LISSOutputer. The supported TimeSeriesBlocks are
   * MiniSeed and TraceBuf
   *
   * @param buf A buffer with the raw data bytes
   * @param len The length of the buffer in bytes
   * @param cl THe class of the buffer (MiniSeed or Tracebuf!)
   * @param oor If not zero, this packet is out of order
   */
  @Override
  public void queue(byte[] buf, int len, Class cl, int oor) {
    if (cl.getName().indexOf("MiniSeed") > 0) {
      try {
        if (dbg || MiniSeed.crackSeedname(buf).substring(0, 6).equals(ChannelHolder.getDebugSeedname())) {
          par.prt("LO: Queue()" + new MiniSeed(buf).toString().substring(0, 66));
        }
        server.queue(buf, len);
        npacket++;
      } catch (IllegalSeednameException e) {
        par.prt("Illegal seedname to queue() as Miniseed");
      }
    } else {
      par.prt("Illegal TimeSeries class sent to LISSOutputer =" + cl.getName());
    }
  }

  /**
   * this is the general call to put data in this LISSOutputer. The supported TimeSeriesBlocks are
   * MiniSeed and TraceBuf
   *
   * @param ms A TimeSeries block to satisfy ChannelInfrastructure but really must be 512 byte
   * miniseed
   * @param oor If not zero, this packet is out of order.
   */
  @Override
  public void queue(TimeSeriesBlock ms, int oor) {
    if (ms instanceof MiniSeed) {
      if (ms.getBlockSize() == 512) {
        server.queue(ms.getBuf(), 512);
      } else {
        SendEvent.edgeSMEEvent("LISSBadForm", "LISSOutput miniseed is not 512 bytes ms=" + ms, this);
      }
      npacket++;
    } else {
      par.prt("Illegal TimeSeries class sent to LISSOutputer =" + ms);
      SendEvent.edgeSMEEvent("LISSBadForm", "LISSOutput is not miniseed =" + ms, this);
    }
  }
}
