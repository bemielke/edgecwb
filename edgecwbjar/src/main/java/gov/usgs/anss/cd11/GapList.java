/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.cd11;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
/**  This class tracks a series of gaps for the CD1.1 format. The gaps are tracked 
 * as longs with lowest (inclusive) and highest (exclusive) sequence numbers. 
 * That is the gap goes from the first missing sequence to the the next present sequence per CD1.1
 * The program interacts
 * by creating a "new gap" when a sequence above the last sequence is received, and by 
 * submitting each received sequence to this object to keep the gaps updated (hopefully 
 * shrinking!).
 * 
 * This is in CD1.1 parlance a frame set (lowest, highest and list of gaps).
 *
 * @author davidketchum
 */
public class GapList {
  public static int MAX_GAPS=4000;             // CD1.1 C code seems to think this is the upper bound, can be overriden by arg to CD11ConnectionServer
  private final ArrayList<Gap> gaps = new ArrayList<>(10);     // list of active gaps
  private final ArrayList<Gap> freeList = new ArrayList<>(10); // place for gaps that are not on the gap list for pool managment
  private boolean dbg=false;
  private int series;
  private String filename;
  private RawDisk gapFile;
  private EdgeThread par;
  private int state;
  private long lowestSeq;
  private final StringBuilder tmpsb = new StringBuilder(1000);
  private long highestSeq;
  public int inState() {return state;}
  public void setDebug(boolean t) {dbg=t;}
  public static void setGapLimit (int n) {MAX_GAPS=n;Util.prt("*** Gap size limit is now "+MAX_GAPS);}
  public synchronized int getGapCount() {return gaps.size();}
  public synchronized long getLowestSeq() {return lowestSeq;}
  public synchronized long getHighestSeq() {return highestSeq;}
  public synchronized int getSeries() {return series;}
  public synchronized long getLowSeq(int i) {
    if(i >= gaps.size()) {
      new RuntimeException("attempt to get gap from one too big i="+i+" size="+gaps.size()).printStackTrace();
      return -1;
    }
    
    return gaps.get(i).getLow();}
  public synchronized long getHighSeq(int i) {
    if(i >= gaps.size()) {
      new RuntimeException("attempt to get gap from one too big i="+i+" size="+gaps.size()).printStackTrace();
      return -1;
    }
    return gaps.get(i).getHigh();
  }
  public String getFilename() {return filename;}
  public synchronized long getGapPacketCount() {
    long total=0;
    for (Gap gap : gaps) {
      total += gap.getHigh() - gap.getLow();
    }
    return total;
  }
  /** this called when rcv ack come in and if we do not know where we are, 
   * we create a frameset with a whole range gap.  We also trim our set to match
   * the low and hi reflected from the other end.
   * 
   * @param lo The low seq in rcv frame set
   * @param hi The high seq in rcv frame set
   * @param ngap Number of gap received
   * @param lows The low end of gaps received
   * @param highs the high end of gaps received
   * @param tag2 A tag to add to the SendEvents
   * @return true if the low and high indicated a new frameset
   */
  public synchronized boolean rcvAck(long lo, long hi, int ngap, long [] lows, long [] highs, String tag2) {
    if(lowestSeq <=0 ) {    // We have an empty frame set
      if(lo > 0) lowestSeq=lo;// If the receive frame set has a low, adopt it.
      if(highestSeq <=0) {    // if the receive frame set has a high, we shou adop
        par.prt(Util.clear(tmpsb).append("**** reset GL:rcvAck: out of range was ").
                append(lowestSeq).append("-").append(highestSeq).
                append(" now ").append(lo).append("-").append(hi).append(" file=").append(filename));
        if(hi > 0) {
          highestSeq=hi;
          newGap(lo, hi);
        }
      }
    }
    if(lo >0 && hi > 0) {
      // Discard any gaps that are not in the other ends frame set
      for(int i=gaps.size()-1; i>=0; i--) {
        if(/*gaps.get(i).getLow() > hi|| */ gaps.get(i).getHigh() < lo - 100000) {   // do not eliminate high ones, sometimes their upper is not high enough
          par.prta(Util.clear(tmpsb).append("**** GL:rcvAck: Discard gap out of other ends frameset ").
                  append(gaps.get(i).toStringBuilder(null)).append(" ").append(lo).append("-").append(hi)); 
          free(i);
        }
      }
      // Trim our gaps so they do not lap outside of the other ends frame set
      for(int i=gaps.size()-1; i>=0; i--) {
        if(gaps.get(i).getLow() < lo-86400 ) {
          par.prta(Util.clear(tmpsb).append(" **** GL:rcvAck: Set new low to other ends low ").
                  append(gaps.get(i).toStringBuilder(null)).append(" low=").append(lo));
          gaps.get(i).setLow(lo-86400);
          if(gaps.get(i).getLow() <= lowestSeq) gaps.get(i).setLow(lowestSeq+1);
        }
        //if(gaps.get(i).getHigh() > hi) {par.prta(" **** GL:rcvAck: Set new high to other ends high "+gaps.get(i)+" low="+hi);gaps.get(i).setHigh(hi);}
        if(gaps.get(i).getLow() >= gaps.get(i).getHigh()) {
          par.prta(Util.clear(tmpsb).append(" **** GL: set new low/high results in deleting gap ").
                  append(gaps.get(i).getLow()).append("-").append(gaps.get(i).getHigh()));
          free(i);
        }
      }
      // this highestSeq+1 != lo is to cut off the slidding windows we saw Sep 2009 from AFTAC
      if( (highestSeq < lo || lowestSeq >  hi || highestSeq > hi+5000) /*&& highestSeq+1 != lo*/) { // We must be talking about different framesets!
        par.prt(Util.clear(tmpsb).append("**** override  GL:rcvAck: out of range was ").append(lowestSeq).
                append("-").append(highestSeq).append(" now ").append(lo).append("-").
                append(hi).append(" file=").append(filename));
         lowestSeq=lo;
        highestSeq=hi;
        for(int i=gaps.size()-1; i>=0; i--) free(0);  /* remove all gaps */
        for(int i=0; i<ngap; i++) newGap(lows[i], highs[i]);// adopt received gaps
        if( (hi - lo) > 8640) lo = hi - 8640;
        par.prt(Util.clear(tmpsb).append("***** override GL: set initial gap to low range (may have been trimmed to 1 day) ").
                append(lo).append("-").append(hi));
        this.addGap(lo, hi+1);    // need to include from low to high+1
        return true;
      }
      if(lowestSeq < lo) {
        par.prt(Util.clear(tmpsb).append(" GL: reset low and trim gap list new low=").
                append(lo).append(" ").append(lowestSeq));
        trimList(Math.max(1,lo-86400));

      }  // note: this will set lowestSeq=lo-86400
      // As of Jan 2011, do not let lowestSeq track their lowest, but allow it to reflect our actual receipts
      // If they suddenly lower the sequence, it might be a reset at their end, but they are suppose to send these "edge" gaps
      // Subject to day limits.
      if(lo < lowestSeq) {par.prt(Util.clear(tmpsb).append(" GL: ***** found a RcvAck low lower than our lowest=").
              append(lowestSeq).append(" new lo=").append(lo));/*lowestSeq=lo;*/ }
      // look for leap ahead that might be a frame reset and warn user.  This does not actually
      // invalidate the frame set as it could be a long telemetry delay jumping forward and all is still well
      if(hi > highestSeq + 2000) {
        par.prt(Util.clear(tmpsb).append("**** change in frame set possible - high lept forward hi=").
                append(hi).append(" last hi=").append(highestSeq));
        SendEvent.debugEvent("CD11SuspFrm", tag2+" frame set has lept ahead suspiciously, please check", "GapList");
        return true;
      }
    }
    return false;
  }
  /** Allow user to association a long time with this gap
   * 
   * @param i  The gap index
   * @param time The time to associate
   */
  public synchronized void setLastTime(int i, long time) {gaps.get(i).setLastTime(time);}
  /** Get a the time last set by the user for this gap
   * 
   * @param i  The gap index
   * @return  A long representing a time
   */
  public synchronized long getLastTime(int i) {return gaps.get(i).getLastTime();}
  public synchronized void clearGapsOnly() {
    gaps.clear();
  }
  public synchronized void clear(int ser) {
    gaps.clear();
    series=ser;
    lowestSeq=-1;
    highestSeq=-1;
  }
  /** this creates a gap list with no gaps in it 
   * @param ser The series of the gaps
   * @param seq The starting sequence, set to -1 if unknown
   * @param file THe gap file to read and write backing this list, if nul do not use such a file
   * @param parent The EdgeFile to use for logging.
   * @throws FileNotFoundException if file is not found
   */
  public GapList(int ser, long seq, String file, EdgeThread parent) throws FileNotFoundException {
    filename=file;
    par = parent;
    if(file != null) gapFile = new RawDisk(file, "rw");
    series = ser;
    lowestSeq=seq;    // if there is no gap file, the given series and 
    highestSeq=seq;   //sequence must be the known beginning

    try {
      if(file != null && gapFile.length() > 0) {
        byte [] b = gapFile.readBlock(0, (int) gapFile.length());
        BufferedReader in = new BufferedReader(new StringReader(new String(b)));
        par.prta(Util.clear(tmpsb).append("GL: Gap file2=").append(file).append(" read=").append(new String(b)));
        String line = in.readLine();
        if(line.contains("GapList sz")) {   // it is a correctly formatted file
          String [] parts = line.split("=");
          String [] sizeParts = parts[1].split(" ");
          int size = Integer.parseInt(sizeParts[0].trim());
          sizeParts = parts[2].split(" ");
          series = Integer.parseInt(sizeParts[0].trim());
          if(series != ser) {     // This is a new series, the old gap list does not matter!
            par.prta("  GL: **** Series in gap file does not match current series - discard it");
          }
          else {    // Series does match, read it in
            sizeParts =parts[3].split(" ");
            lowestSeq = Long.parseLong(sizeParts[0].trim());
            sizeParts = parts[4].split(" ");
            highestSeq = Long.parseLong(sizeParts[0].trim());
            for(int i=0; i<size; i++) {
              line = in.readLine();
              if(line != null) {
                parts = line.split("-");
                long low = Long.parseLong(parts[0].trim());
                newGap(low, Long.parseLong(parts[1].trim()));
              }
            }
            if(lowestSeq <= 0) lowestSeq = 1;     // this should not be zero unless this is the 0,-1 (unknown) list
            par.prt(Util.clear(tmpsb).append("GapList created="));
            par.prt(toStringBuilder(Util.clear(tmpsb)).insert(0,"GL: created "));
          }
          par.prt(Util.clear(tmpsb).append("GL: GapList cons file=").append(file).append(" "));
          par.prt(toStringBuilder(Util.clear(tmpsb)).insert(0,"GL: GapList cons file "));
        }
      }
      else {
        par.prt(Util.clear(tmpsb).append("GL: GapList is new file=").append(file));
        lowestSeq=0;
        highestSeq=-1;
        writeGaps(false);
      }     // its a new file, write it out.
    }
    catch(IOException e) {
      Util.IOErrorPrint(e,"GL:  Getting gaps from file="+ file);
    }
  }
  /** This is for processing a gap set (ack) coming from the receivers where we are primary sender
   * 
   * @param lo Low end of the frame set
   * @param hi High end of the frame set
   * @param ngap Number of gaps in lows and highes
   * @param lows The starting sequence for the gap (inclusive)
   * @param highs The endding sequenc of the gap (the last sequence received)
   * @param tag2 A labling tag
   */
  public synchronized void rcvGapSet(long lo, long hi, int ngap, long [] lows, long [] highs, String tag2) {
    if(lo > lowestSeq) {
      lowestSeq=lo;
    }
    if(hi > highestSeq) {
      highestSeq = hi;
    }
    // Now we need totry to add all of the gaps to the gap set.  Do from newest to oldest incase it overflows 1000 gaps
    for (Gap gap : gaps) {
      gap.clearMark();
    }
    for(int i=ngap-1; i>=0; i--) {
      newGap(lows[i], highs[i]);
    }
    for(int i=gaps.size()-1; i>=0; i--) if(!gaps.get(i).getMark()) {
      if(dbg) par.prt(Util.clear(tmpsb).append("remove ").append(gaps.get(i).toStringBuilder(null)));
      gaps.remove(i);
    }  // this gap is not on remotes list, remove it
    Collections.sort(gaps);
  }
  public final synchronized void writeGaps(boolean reopen) {
    if(filename == null) return;      // no file given
    if(reopen) {
      try{
        if(gapFile != null) gapFile.close();
        gapFile = new RawDisk(filename, "rw");
      }
      catch(IOException e) {
        par.prta("Error closing or opening gap file in writeGaps file="+filename+" e="+e);
        e.printStackTrace(par.getPrintStream());
        return;
      }
    }
    
    try {
      gapFile.position(0);
      StringBuilder s = toStringBuilder(null);
      for(int i=0; i<s.length(); i++) gapFile.write((byte) s.charAt(i));
      gapFile.setLength(s.length());
    }
    catch(IOException e) {
      Util.IOErrorPrint(e,"Writing gap file="+filename);
    }
  }
  /** create a string with the gap list
   * @return
   */
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public final synchronized StringBuilder toStringBuilder(StringBuilder tmp) {
    state=1;
    Collections.sort(gaps);
    state=2;
    StringBuilder sb = tmp;
    if(tmp == null) sb = Util.clear(tmpsb);
    state=3;
    //Util.cleanIP(sb);
    //synchronized(sb) {
      sb.append("GapList sz=").append(gaps.size()).append(" ser=").append(series).append(" ");
      sb.append("Low=").append(lowestSeq).append(" ");
      sb.append("High=").append(highestSeq).append("\n");
      state=4;
      for (Gap gap : gaps) {
        sb.append(gap.getLow()).append("-").append(gap.getHigh()).append("-").append(gap.getHigh() - gap.getLow()).
                append(" ").append(highestSeq - gap.getLow()).
                append(gap.getLastTime() > 0 ? " " + (System.currentTimeMillis() - gap.getLastTime()) / 1000 : "").append("\n");
      }
      state=5;
    //}
    return sb;
  }
  public synchronized int trimList(long mostSeq) {
    int nfree=0;
    while(gaps.size() > MAX_GAPS) {
      par.prta(Util.clear(tmpsb).append("GL: **** Drop oldest gap in trimlist max=").
              append(MAX_GAPS).append(" ").append(gaps.get(0).toStringBuilder(null)));
      free(0);
      nfree++;
    }
    for(int i=gaps.size()-1; i>=0; i--) {
      if(gaps.get(i).getLow() < mostSeq) gaps.get(i).setLow(mostSeq);
      if(gaps.get(i).getHigh() < mostSeq) {
        if(dbg) par.prt(Util.clear(tmpsb).append("GL: Free gap as too old ").append(gaps.get(i).toStringBuilder(null)));
        free(i);
        nfree++;
      }
      if(mostSeq > lowestSeq) lowestSeq = mostSeq;
    }
    return nfree;
  }
  /** put a new gap on the gap list with the given range.  Internally this
   * might extend an existing gap or create a new one as appropriate.
   * 
   * @param low Lowest seq  in the gap
   * @param high Highest seq in the gap
   */
  private synchronized void newGap(long low, long high) {
    boolean added=false;
    for(int i=0; i<gaps.size(); i++) {
      if(gaps.get(i).tryAdd(low,high)) {
        added=true; 
        if(dbg) 
          par.prt(Util.clear(tmpsb).append("GL: gap extends i=").append(i).append(" ").append(gaps.get(i).toStringBuilder(null))); 
        gaps.get(i).setMark();
        break;
      }
    }
    if(!added) {
      if(dbg) par.prt(Util.clear(tmpsb).append("GL: new gap low=").append(low).append(" ").
              append(high).append(" len=").append(high-low).append(" sz=").append(gaps.size()));
      addGap(low,high);
    }
  }
  /** add a gap to the gap list and set values 
   * 
   * @param low low seq
   * @param high sequence
   */
  private synchronized void addGap(long low, long high) {
    while(gaps.size() > MAX_GAPS) {
      par.prta(Util.clear(tmpsb).append("GL: Oldest Gap limit is ").append(MAX_GAPS).
              append(" ").append(gaps.get(0).toStringBuilder(null)));
      free(0);
    }
    if(low <=0 || high <= 0) return;
    if(freeList.isEmpty()) {
      Gap g = new Gap(low,high);
      g.setMark();
      gaps.add(g);
    }
    else {
      Gap g = freeList.get(freeList.size() -1);
      g.setMark();
      freeList.remove(freeList.size() -1);
      g.setLow(low);
      g.setHigh(high);
      for(int i=0; i<gaps.size(); i++) {
        if(gaps.get(i).getLow() > low) {        // Found its position on the list
          gaps.add(i, g);                       // Insert the gap in ordered position
          return;
        }
      }
      gaps.add(g);                      // add it to the , if not inserted
    }
    
  }
  private synchronized void free(int i) {
    freeList.add(gaps.get(i));
    gaps.remove(i);
  }
  public int gapBufferSizeNeeded() {return gaps.size()*16+4;}
  /** return the gap portion of the ack packet (from # gaps through gap list)
   * 
   * @param buf  User buffer to put the gap information into
   * @return The length of the buf used.
   */
  public synchronized int getGapBuffer(byte [] buf) {
    for(int i=gaps.size()-1; i>=0; i--) {
      if(/*gaps.get(i).getLow() < lowestSeq  ||*/ gaps.get(i).getHigh() < lowestSeq ||
         /*gaps.get(i).getHigh() > highestSeq ||*/ gaps.get(i).getLow() > highestSeq) {
        par.prta(Util.clear(tmpsb).append("GL: **** DropGap ").append(gaps.get(i).toStringBuilder(null)).
                append(" out of ").append(lowestSeq).append("-").append(highestSeq).append(" range"));
        free(i);
      }
    }
    if(buf.length < gaps.size()*16+4) buf = new byte[gaps.size()*16+100]; // make it bigger
    ByteBuffer bb = ByteBuffer.wrap(buf);
    bb.position(0);
    bb.putInt(gaps.size());
    for (Gap gap : gaps) {
      bb.putLong(gap.getLow());
      bb.putLong(gap.getHigh());
    }
    return gaps.size()*16+4;
  }
  /** the processing software calls this routine for each sequence received so it can be
   * marked off any gap in the gap list.  This trims the gap list when the gap are filled
   * and splits gaps if the sequence is not on the end of a gap.
   * 
   * @param seq The sequence to consider for all gaps.
   * @return 0=expected, 1= new gap , 2= in gap low, 3=in gap high, 4=Split gap, 5= not in frameset,6=in frame set but not in gap
   */
  public synchronized int gotSeq(long seq) {
    // for each gap on the active list
    if(lowestSeq == 0 || lowestSeq > seq) {
      //if(dbg)
        par.prta(Util.clear(tmpsb).append("GL: **** set new lowest seq based on received seq was ").append(lowestSeq).append(" now ").append(seq));
      long oldLowest=lowestSeq;
      lowestSeq=Math.min(seq,highestSeq);
      newGap(lowestSeq, oldLowest);
      par.prta(Util.clear(tmpsb).append("GL: **** create new low end gap ").append(lowestSeq).append("-").append(oldLowest));
      return 5;
    }
    if(seq == highestSeq+1) { // is it the expected next seq
      //if(dbg) par.prta("GL: exp sq="+seq);
      highestSeq = seq;
      return 0;
    }
    else if(seq > highestSeq+1) {
      newGap(Math.max(1, highestSeq+1), seq);
      par.prta(Util.clear(tmpsb).append("GL: new gap from ").append(highestSeq+1).append(" to ").append(seq));
      highestSeq=seq;
      // look for leap ahead that might be a frame reset and warn user.  This does not actually
      // invalidate the frame set as it could be a long telemetry delay jumping forward and all is still well
      if(seq > highestSeq + 2000) {
        par.prt(Util.clear(tmpsb).append("**** change in frame set possible - data lept forward seq=").
                append(seq).append(" last hi=").append(highestSeq));
        SendEvent.debugEvent("CD11SuspFrm", " Data has lept ahead suspiciously, please check", "GapList");
      }
      return 1;     // this cannot be in the gaps
    }
    for(int i=0; i<gaps.size(); i++) {
      // is this sequence in this gap
      if(seq >= gaps.get(i).getLow() && seq < gaps.get(i).getHigh()) { // it is in this range of this gap
        if(gaps.get(i).getLow() == seq) {                       // is it the low seq
          if(dbg) par.prta(Util.clear(tmpsb).append("GL: gotSeq: low end ").append(seq).
                  append(" ").append(gaps.get(i).toStringBuilder(null)).append(" #gaps=").append(gaps.size()));
          gaps.get(i).setLow(seq+1);
          if(gaps.get(i).getLow() >= gaps.get(i).getHigh()) {    // No more left in gap
            if(dbg) par.prt(Util.clear(tmpsb).append("      GL: gap filled LOW ").append(i).
                    append(" ").append(gaps.get(i).toStringBuilder(null)).append(" #gaps=").append(gaps.size()));
            free(i);
          }
          return 2;
        }
        else if(gaps.get(i).getHigh()-1 == seq) {    // its on the high end
          if(dbg) par.prta(Util.clear(tmpsb).append("GL: gotSeq: high end ").append(seq).
                  append(" ").append(gaps.get(i).toStringBuilder(null)).append(" #gaps=").append(gaps.size()));
          gaps.get(i).setHigh(seq);        // reduce high end
          if(gaps.get(i).getHigh() <= gaps.get(i).getLow()) {
            if(dbg) par.prta(Util.clear(tmpsb).append("      GL: gap filled HI ").append(i).
                    append(" ").append(gaps.get(i).toStringBuilder(null)).append(" #gaps=").append(gaps.size()));
            free(i);
          }
          return 3;
        }
        // it must be in the middle of the gap, split the gap into two p
        else if(gaps.get(i).getLow() < seq && gaps.get(i).getHigh() > seq ) { // is it in the middle (it has to be), split the gap
          // the gap must be split up
          long saveHigh = gaps.get(i).getHigh();
          if(dbg) 
            par.prta(Util.clear(tmpsb).append("GL: gotSeq: split=").append(seq).
                  append(" ").append(gaps.get(i).toStringBuilder(null)).append(" #gaps=").append(gaps.size()));
          gaps.get(i).setHigh(seq);           // reset high of current gap
          addGap(seq+1, saveHigh);   // add a new gap starting a seq+1 to old high
          if(dbg) 
            par.prta(Util.clear(tmpsb).append("     GL: into ").append(gaps.get(i).toStringBuilder(null)).
                  append(" new gap ").append(seq+1).append("-").append(saveHigh).append(" #gaps=").append(gaps.size()));
          return 4;
        }
        par.prta(Util.clear(tmpsb).append("GL: This cannot happen seq=").append(seq).
                append(" low=").append(gaps.get(i).getLow()).append(" high=").append(gaps.get(i).getHigh()));
      }
    }     // end of for on gaps
    if(dbg) 
      par.prta(Util.clear(tmpsb).append("GL: no gap for seq=").append(seq).append(" "/*+toString()*/));
    return 5;
  }
  
  /** this inner class is used to actually track each gap
   * Gaps run from low (the first sequence missing) to high (the next sequence present)*/
  public class Gap implements Comparable {
    long lowSeq;
    long highSeq;
    long lastTime;      // save a time for the user
    boolean mark;       // sav a boolean for the user
    StringBuilder tsb = new StringBuilder(40);
    public void setLow(long seq) {lowSeq = seq;}
    public void setHigh(long seq) {highSeq = seq;}
    public long getLow() {return lowSeq;}
    public long getHigh() {return highSeq;}
    public void setLastTime(long time) {lastTime = time;}
    public long getLastTime() {return lastTime;}
    public void clearMark() {mark=false;}
    public void setMark() {mark = true;}
    public boolean getMark() {return mark;}

    @Override
    public String toString() {return toStringBuilder(null).toString();}
    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if(tmp == null) sb = Util.clear(tsb);
      sb.append("Gap: from ").append(lowSeq).append("-").append(highSeq);
      return sb;
    }
    /** create a new gap with the given sequence range inclusive
     * 
     * @param low Lowest seq in the gap
     * @param hi The highest seq in the gap
     */
    public Gap(long low, long hi)  {
      lowSeq=low;
      highSeq = hi;
    }
    /** try to add the following gap to the existing gaps, return true if successful
     * 
     * @param low Lowest seq in the gap
     * @param high The highest seq in the gap
     * @return true, if this extended an existing gap
     */
    public boolean tryAdd(long low, long high) {
      if(low> highSeq) return false;
      if(high < lowSeq -1) return false;
      if(dbg && (lowSeq != low || highSeq != high)) 
        par.prt(Util.clear(tsb).append("GL:Gap: tryAdd ").append(low).append("-").append(high).
                append(" extends ").append(lowSeq).append("-").append(highSeq));
      lowSeq = Math.min(lowSeq,low);
      highSeq = Math.max(highSeq, high);
      return true;
    }
    @Override
    public int compareTo(Object o2) {
      Gap o = (Gap) o2;
      if(getLow() < o.getLow() ) return -1;
      if(getLow() > o.getLow()) return 1;
      return 0;
    }
  }
}
