  /*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */



package gov.usgs.anss.cd11;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.util.ArrayList;
/** This keeps track of a series of gaps lists based on some series sequence
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */

public class SeriesGapLists {

  public static int MAX_GAPS=1000;             // CD1.1 C code seems to think this is the upper bound, can be overriden by arg to CD11ConnectionServer
  private static final ArrayList<GapList> gapslist = new ArrayList<>(10);     // list of active gaps
  private final boolean dbg=true;
  private final EdgeThread par;
  private final String filename;
  private final StringBuilder sb = new StringBuilder(500);
  public static void setGapLimit (int n) {
    MAX_GAPS=n;
    Util.prt("*** Gap size limit is now "+MAX_GAPS);
    for (GapList gapslist1 : gapslist) {
      GapList.setGapLimit(MAX_GAPS);
    }
  }
  private GapList getGapList(int ser) {
    for (GapList gapslist1 : gapslist) {
      if (gapslist1.getSeries() == ser) {
        return gapslist1;
      }
    }
    return null;
  }
  public synchronized int getGapCount(int series) {return getGapList(series).getGapCount();}
  public synchronized long getLowestSeq(int series) {return getGapList(series).getLowestSeq();}
  public synchronized long getHighestSeq(int series) {return getGapList(series).getHighestSeq();}
  public synchronized long getLowSeq(int series,int i) {return getGapList(series).getLowSeq(i);}
  public synchronized long getHighSeq(int series, int i) {return getGapList(series).getHighSeq(i);}
  public String getFilename() {return filename;}
  public synchronized long getGapPacketCount(int series) {
    return getGapList(series).getGapPacketCount();
  }
  /** this called when rcv ack come in and if we do not know where we are,
   * we create a frameset with a whole range gap.  We also trim our set to match
   * the low and hi reflected from the other end.
   *
   * @param series Serias number
   * @param lo The low seq in rcv frame set
   * @param hi The high seq in rcv frame set
   * @param ngap Number of gap received
   * @param lows The low end of gaps received
   * @param highs the high end of gaps received
   * @param tag2 A tag to add to the SendEvents
   * @return true if the low and high indicated a new frameset
   */
  public boolean rcvAck(int series, long lo, long hi, int ngap, long [] lows, long [] highs, String tag2) {
    return getGapList(series).rcvAck(lo, hi, ngap, lows, highs, tag2);
  }
  /** Allow user to association a long time with this gap
   * @param series number
   * @param i  The gap index
   * @param time The time to associate
   */
  public synchronized void setLastTime(int series,int i, long time) {getGapList(series).setLastTime(i,time);}
  /** Get a the time last set by the user for this gap
   *
   * @param series series number
   * @param i  The gap index
   * @return  A long representing a time
   */
  public synchronized long getLastTime(int series, int i) {return getGapList(series).getLastTime(i);}
  public synchronized void clear(int ser) {
    getGapList(ser).clear(ser);
  }
  /** create a new series gap list
   * @param ser The series of the gaps
   * @param seq The starting sequence, set to -1 if unknown
   * @throws FileNotFoundException if file is not found
   *
   */
  public void addGapList( int ser, long seq) throws FileNotFoundException {
    gapslist.add(new GapList(ser,seq, filename, par));
  }
  /** this creates a gap list with no gaps in it
   * @param ser The series of the gaps
   * @param seq The starting sequence, set to -1 if unknown
   * @param file THe gap file to read and write backing this list, if nul do not use such a file
   * @param parent The EdgeFile to use for logging.
   * @throws FileNotFoundException if file is not found
   */
  public SeriesGapLists(int ser, long seq, String file, EdgeThread parent) throws FileNotFoundException {
    filename=file;
    par = parent;
    gapslist.add(new GapList(ser, seq, file, parent));

  }
  /** This is for processing a gap set (ack) coming from the receivers where we are primary sender
   *
   * @param series series number
   * @param lo Low end of the frame set
   * @param hi High end of the frame set
   * @param ngap Number of gaps in lows and highes
   * @param lows The starting sequence for the gap (inclusive)
   * @param highs The endding sequenc of the gap (the last sequence received)
   * @param tag2 A labling tag
   */
  public synchronized void rcvGapSet(int series, long lo, long hi, int ngap, long [] lows, long [] highs, String tag2) {
    getGapList(series).rcvGapSet(lo, hi, ngap, lows, highs, tag2);
  }
  public synchronized void writeGaps(int series, boolean reopen) {
    getGapList(series).writeGaps(reopen);
  }
  /** create a string with the gap list
   * @return
   */
  @Override
  public String toString() {
    if(sb.length() > 0) sb.delete(0,sb.length());
    for (GapList gapslist1 : gapslist) {
      sb.append(gapslist1.toString()).append("\n");
    }
    return sb.toString();
  }
  public synchronized void trimList(int series, long mostSeq) {
    getGapList(series).trimList(mostSeq);
  }

  public int gapBufferSizeNeeded(int series) {return getGapList(series).gapBufferSizeNeeded();}
  /** return the gap portion of the ack packet (from # gaps through gap list)
   * @param series Series number
   * @param buf  User buffer to put the gap information into
   * @return The length of the buf used.
   */
  public int getGapBuffer(int series, byte [] buf) {
    return getGapList(series).getGapBuffer(buf);
  }
  /** the processing software calls this routine for each sequence received so it can be
   * marked off any gap in the gap list.  This trims the gap list when the gap are filled
   * and splits gaps if the sequence is not on the end of a gap.
   *
   * @param series Series number
   * @param seq The sequence to consider for all gaps.
   * @return 0=expected, 1= new gap , 2= in gap low, 3=in gap high, 4=Split gap, 5= not in frameset,6=in frame set but not in gap
   */
  public synchronized int gotSeq(int series, long seq) {
    return getGapList(series).gotSeq(seq);
  }
  
}
