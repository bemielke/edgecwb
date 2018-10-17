/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import java.util.TreeMap;
import java.util.Iterator;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgemom.Hydra;

/**
 * This calls reads from a edge created output buffer and keeps the data sorted into channels. It
 * manages to keep a predefined amount of data per channel and support access classes to implement a
 * "wait for time" release (that is the consumer specifies a time its willing to wait for the next
 * packet on the channel and when that expires is willing to take the next oldest. All data that
 * comes into the Edge/CWB system which is not specifically set to be an in-order source, would use
 * this class to sort out the data for the output methods that want the data in order (hydra and
 * LISS particularly).
 *
 * @author davidketchum
 */
public final class ChannelInfrastructure {

  private final TreeMap<String, ChannelHolder> channels = new TreeMap<>();
  private final String name;
  private final int secDepth;
  private final int minrecs;
  private final Class defaultSubscriber;
  private static EdgeThread par;  // link to parent edgethread for logging, passed out to others
  // via getParent() for the same purpose
  private final int defWaitSec;         // default constructor wait depth
  private boolean terminate;

  public void shutdown() {
    terminate = true;
    synchronized (channels) {
      Iterator<ChannelHolder> itr = channels.values().iterator();
      par.prta("CI : is shuttdown down " + channels.size() + " channels.");
      while (itr.hasNext()) {
        itr.next().shutdown();
        itr.remove();
      }
    }

  }

  /**
   * return the parent EdgeThread (usually for linkage to logging system
   *
   * @return The parent EdgeThread
   */
  public static EdgeThread getParent() {
    return par;
  }

  /**
   * string representing state of this class
   *
   * @return the string
   */
  @Override
  public String toString() {
    return " CI: " + name + " #chan=" + channels.size() + " depth=" + secDepth + " s. minRecs=" + minrecs;
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  public String getMonitorString() {
    return "ChannelInfraNChan=" + channels.size() + "\n";
  }

  /**
   * Creates a new instance of ChannelInfrastructure
   *
   * @param nm The name to refer to this channel infrastructure. (often a ring file name)
   * @param secD The seconds of depth in the wait-for-the-bus algorithm (max time to wait for old
   * data to fill in)
   * @param minrc The minimum number of records of depth
   * @param defSub The class that will be processing this data as output. A new one of these is
   * created as new channels are found.
   * @param wait Number of seconds to wait by default in the defSub constructor.
   * @param parent The logging parent
   */
  public ChannelInfrastructure(String nm, int secD, int minrc, Class defSub, int wait, EdgeThread parent) {
    name = nm;
    secDepth = secD;
    minrecs = minrc;
    par = parent;
    defaultSubscriber = defSub;
    defWaitSec = wait;

  }

  public void addData(TimeSeriesBlock in) {
    if (terminate) {
      return;        // Do not process after termination has started
    }   //String comp = in.getSeedNameString().substring(7,10);
    //char c = comp.charAt(1);
    if (!Hydra.okChannelToHydra(in.getSeedNameString())) {
      return;
    }
    //if(c != 'H' && c != 'L' && c != 'N' && c != 'D') return;
    //c = comp.charAt(0);
    //if(c != 'B' && c != 'L' && c != 'V' && c != 'E' && c != 'S' && c != 'H' && c != 'U' && c != 'N') return;

    //if(comp.substring(0,1).equals("A") || comp.substring(0,1).equals("V")) return;
    //if( comp.equals("ACE") || comp.equals("LOG") || comp.equals("LDO") || 
    //    || comp.substring(1,2).equals("N") || 
    //|| comp.substring(0,2).equals("LC") || 
    //comp.substring(0,2).equals("LE") || comp.substring(0,2).equals("UF")) return;
    ChannelHolder holder = channels.get(in.getSeedNameString());
    // if(in.getSeedNameString().startsWith("AY")) par.prt("CINfra got "+in.toStringBuilder(null)+" hold="+holder);
    if (in.getNsamp() <= 0 || in.getRate() <= 0.) {
      return;
    }
    if (holder == null) {

      holder = new ChannelHolder(in, secDepth, minrecs, defaultSubscriber, defWaitSec);
      synchronized (channels) {
        channels.put(in.getSeedNameString(), holder);           // Add it to list of channel holders
      }
    } else {
      holder.addData(in);
    }
  }
}
