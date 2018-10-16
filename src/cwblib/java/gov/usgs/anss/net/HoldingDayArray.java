/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.net;

import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;
import java.util.Collections;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.GregorianCalendar;
/**
 * This class holds a lot of the  holdings so they can be updated while in MySQL
 * backed objects.  The array of holdings in managed and kept in 1st degree order by
 * the seedname//type.  Within the same seedname//type there may be many records but
 * without any particular ordering for time.  The class has several maintenance routines
 * that purge out Holdings that are not being updated, and one that attempts to 
 * consolidate holdings which may have grown to touch or overlap.  These routines
 * should be called fairly sparingly to keep things cleaned up.
 *
 * @author davidketchum
 */
public class HoldingDayArray {
  static int debugValue;
  private final TLongObjectHashMap<ArrayList<HoldingDay>> chans = new TLongObjectHashMap<>(10000);
  //Holding [] h;
  private final int myValue;
  //private int nh;         // Number of used eliments in h
  private String tag;
  //private Holding hempty;
  private int state;
  private Object [] dest;
  static long lastPage;
  private boolean dbg=false;
  private boolean alwaysAdd;   // flag is used only by intital loader to not do true addOns but always add the day
  public void alwaysAdd() {alwaysAdd=true;}
  public int getState() {return state;}
  @Override
  public String toString() {return "HA: nh="+chans.size()+" state="+state;}
  public String toStringDetail() {
    StringBuilder sb = new StringBuilder(1000);
    synchronized(chans) {
      TLongObjectIterator<ArrayList<HoldingDay>> itr = chans.iterator();
      while(itr.hasNext()) {
        itr.advance();
        ArrayList<HoldingDay> holdings= itr.value();
        for (HoldingDay holding : holdings) {
          sb.append(holding.toStringBuilder(null)).append("\n");
        }
      }
    }  
    return sb.toString();
  }
  /** given a time in GregorianCalendar.getTimeInMillis() form and duration in millis
   *return true if this is entirely within one of the hold holdings. Use this call only if
   * this Holding array contains multiple seedname.
   *@param seedname The seedname to test for
   *@param time A millisecond time per GregorianCalendar
   *@param ms The duration being tested in Millis
   *@return True if the time and duration are entirely within a holding
   */
  public boolean containsFully(StringBuilder seedname,long time, int ms) {
    ArrayList<HoldingDay> list = chans.get(Util.getHashFromSeedname(seedname));
    for(HoldingDay day : list) {
      if(day.completelyContains(time,ms)) return true;
    }
    return false;
  }
  /** given a time in GregorianCalendar.getTimeInMillis() form and duration in millis
   *return true if this is entirely within one of the hold holdings. Use this call only if
   * this Holding array only contains the correct seedname.
   *@param time A millisecond time per GregorianCalendar
   *@param ms The duration being tested in Millis
   *@return True if the time and duration are entirely within a holding
   */
 /* public boolean containsFully(long time, int ms) {
    for(int i=0; i<nh; i++) {
     if(h[i].completelyContains(time,ms)) return true;
    }
    return false;
  }*/
  /** Creates a new instance of HoldingArray */
  public HoldingDayArray() {
    state = 1;
    //h = new Holding[1000];
    myValue=debugValue++;
    //nh=0;
    tag=""+myValue;
    //hempty= new Holding("ZZZZZZZZZZZZ","ZZ", 0L, 0);
    //for(int i=0; i<1000; i++) h[i] = hempty; 
    state=0;
  }
  /*public synchronized void clearNoWrite() {
   for(int i=0; i<nh; i++) h[i]=hempty;
    nh = 0;
  }*/
  public synchronized void setTag(String t) {tag=t;}
  /** given a holding ID, add it to the end of this Holding array
   *@param ID the holdingID
   */
  public synchronized void addEnd(int ID) {
    state=2;
    try {
      HoldingDay a = new HoldingDay(ID);
      addEnd(a);
    }
    catch(InstantiationException e) {
      Util.prt(" *** bad new Holding(ID) e="+e);
    }
    state=0;
  }
  
  /** given a positioned result set, add the represented Holding to the end
   *@param rs The positioned ResultSet
   */
  public synchronized void addEnd(ResultSet rs) {
    state=3;
    HoldingDay a = new HoldingDay(rs);
    addEnd(a);
    state=0;
 }
  /** given a Holding, add it to this HoldingDayArray
   *@param a The Holding to add
   */
  public synchronized void addEnd(HoldingDay a) {
    // If we did not expand an existing one, create a new one
    state=4;
    try {
      ArrayList<HoldingDay> list = chans.get(Util.getHashFromSeedname(a.getSeedName()));
      if(list == null) {
        list = new ArrayList<HoldingDay>(2);
        chans.put(Util.getHashFromSeedname(a.getSeedName()), list);
      }
      list.add(a);
      a.setDeferUpdate(true);
      /*nh=nh+1;
      if(nh > h.length)
      {
        Util.prta("HA:["+tag+"] Expand msgs by 1000 len="+h.length+" nmsgs="+nh);
        Holding [] tmp = new Holding[h.length+1000];
        System.arraycopy(h, 0, tmp, 0, h.length);
        for(int i=0; i<1000; i++) tmp[h.length+i] = hempty;
        h = tmp;
      }


      int insertAt = nh-1;
      //Util.prt("HA:["+tag+"] add holding="+insertAt+" "+a.toString());
      if(insertAt+1 < h.length)
        System.arraycopy(h, insertAt, h, insertAt+1, h.length-insertAt-1);
      h[insertAt]= a;
      if(dbg) Util.prta("HA:["+tag+"] Insert record at "+insertAt+" ps="+h[insertAt].toString2());
      h[insertAt].setDeferUpdate(true);*/
    }
    catch(RuntimeException e) {
      Util.prt("RuntimeException in HoldingArray.addEnd() ="+e.getMessage());
      e.printStackTrace();
      
    }
    state=0;
  }
  private synchronized boolean addOnSingleDay(ArrayList<HoldingDay> list,String name, String ty, long time, long ms) throws HoldingDayException {
    // try to add this to each of the existing holdings on this list, if none, create a new one
    if(alwaysAdd) {
      HoldingDay newday = new HoldingDay(name, ty, time, ms);
      list.add(newday);
      return false;
    }
    boolean update=false;
    int yrdoy = HoldingDay.getYrdoy(time);
    for(HoldingDay day: list) {
      if(yrdoy == day.getYRDOY())
        update |= day.addOn(name,ty, time, ms);
    }
    if(!update) {
      HoldingDay newday = new HoldingDay(name, ty, time, ms);
      list.add(newday);
    }
    return update;
  }
  public synchronized void addOn(String name, String ty, long time, long ms) {
    state=5;
    int ins=-320000000;
    if(name==null) return;
    if(ty == null) return;
    state=51;
    if(!Util.isValidSeedName(name)) {
      Util.prt("HA: holdings array exception back seedname="+name);
      state=6;

      return;   // cannot porcess invalid seed names.
    }
    dbg=false;
    if(name.substring(0,6).equals("IMKS01")) dbg=true;
    try {
      ArrayList<HoldingDay> list = chans.get(Util.getHashFromSeedname(name));
      if(list == null) {
        list = new ArrayList<HoldingDay>(2);
        chans.put(Util.getHashFromSeedname(name), list);
      }

      // Call addOnSingle day for all of the time add ons that are on a single day
      long begin = time;
      long nextMidnight = time/86400000L*86400000L + 86400000L;
      long dur = ms;
      while (begin < time +ms) {
        if(begin + dur > nextMidnight) {   // this lapse into a new day
          long untilMidnight = nextMidnight - begin;
          try {
            addOnSingleDay(list, name, ty, begin, untilMidnight);
          }
          catch(HoldingDayException e) {
            e.printStackTrace();
          }
          dur -= untilMidnight;
          begin = nextMidnight;
          nextMidnight = nextMidnight+86400000L;
        }
        else {
          try {
            addOnSingleDay(list, name, ty, begin, dur );
          }
          catch(HoldingDayException e) {
            e.printStackTrace();
          }
          begin += dur;
        }
      }
      
      /*
      //if(name.substring(2,5).compareTo("AAAAA") == -1) return;
      //if(name.substring(0,7).compareTo("ZZZZZZ") == 1) return;
      state=52;
      if(h == null) return;         // This holdingArray has been closed!
      state=53;
      Holding a= null;
      try {
        a = new Holding(name,ty,time,ms); 
      }
      catch(RuntimeException e) {
        if(e.getMessage()!= null )
          if(e.getMessage().contains("rejected bad")) {
            Util.prt("Holding rejected "+name+" type="+ty+" time="+time); 
            return;
          }
      }
      state=54;
      if(nh == 0) ins = -1;
      else ins = Arrays.binarySearch(h,a);
      state=55;
      if(dbg) Util.prta("HA:["+tag+"]Addon ins="+ins+" seed="+name+" "+ty+" time="+time+" "+ms);
      if(ins >= 0)
      { boolean done=false;
        while(!done) {
          state=56;
          if(ins <= 0 ) {break;}
          if(dbg) Util.prta("HA:["+tag+"]search back for first ins="+ins+" h[ins-1]="+h[ins-1].toString());        
          // If the prior one is still the same type, count done
          if(h[ins-1].getSeedName().equals(name) && h[ins-1].getType().equals(ty)) ins--;
          else done=true;

        }
        state=57;
        done=false;
        boolean update=false;
        while (!done) {
          update = h[ins].addOn(time,ms);
          if(dbg) Util.prta("HA:["+tag+"]Try to add to ins="+ins+" h="+h[ins]+" upd="+update);
          if(update) { break;}
          ins++;
          if(ins >= nh) {break;}
          if(!h[ins].getSeedName().equals(name) || !h[ins].getType().equals(ty)) {ins--;done=true;}
        }
        state=58;
        // If update is false, then this is the start of a new holding, create it
        if(!update) {
          //a = new Holding(name,ty,time,ms);
          if(dbg) Util.prta("HA:["+tag+"] no update do insert ins="+ins);
          ins=-(ins+1);           // fake up so its like the binary search return
        } else if(dbg) Util.prta("HA:["+tag+"] Update done at ins="+ins+" h[ins]="+h[ins].toString());
      }

      // If we did not expand an existing one, create a new one
      if(ins < 0) {
        state =59;
        if(nh+1 > h.length)
        {
          Util.prta("HA:["+tag+"] Expand msgs by 1000 len="+h.length+" nmsgs="+nh);
          Holding [] tmp = new Holding[h.length+1000];
          System.arraycopy(h, 0, tmp, 0, h.length);
          for(int i=0; i<1000; i++) tmp[h.length+i] = hempty;
          h = tmp;
        }
        state=60;
        int insertAt = -(ins +1);
        if(insertAt+1 < h.length)
          System.arraycopy(h, insertAt, h, insertAt+1, h.length-insertAt-1);
        try {
          h[insertAt]= new Holding(name,ty,time,ms);
      
        }
        catch(RuntimeException e) {
          if(e.getMessage() != null)
            if(e.getMessage().contains("rejected bad")) {
              Util.prt("Holding rejected "+name+" type="+ty+" time="+time); 
              return;
            }
        }
        nh=nh+1;
        if(dbg) Util.prta("HA:["+tag+"] Insert record at "+insertAt+" ps="+h[insertAt].toString2());
        h[insertAt].setDeferUpdate(true);
      }*/
    }
    catch(RuntimeException e) {
      Util.prt(tag+" RuntimeException in HoldingArray.addOn()  nm="+name+" ty="+ty+" msg="+e.getMessage());
      /*if(nh > 0) {
        if(ins-1 >= 0 && h[ins-1] != null) Util.prt("ins-1="+h[ins-1]);
        if(ins >= 0 && h[ins] != null) Util.prt("ins="+h[ins]);
      }*/
      if(System.currentTimeMillis() - lastPage > 7200000) {
        SendEvent.pageSMEEvent("HoldArrRuntime","Runtime Exception in HoldingArray.addOn() "+tag, this);
        lastPage = System.currentTimeMillis();
      }
      e.printStackTrace();
    }
    state=0;
  }
  public void list() {
    int nmod=0;
    int ntot=0;
    synchronized(chans) {
      TLongObjectIterator<ArrayList<HoldingDay>> itr = chans.iterator();
      while(itr.hasNext()) {
        itr.advance();
        ArrayList<HoldingDay> holdings= itr.value();
        for (HoldingDay holding : holdings) {
         Util.prt(holding.toString2());
         ntot++;
         if(holding.isModified()) nmod++;
        }
      }
    }  
    Util.prt(nmod+" of "+ntot+" records need writing");
    /*for(int i=0; i<nh; i++) {
      Util.prt(i+" "+h[i].toString2());
      if(h[i].isModified()) nmod++;
    }
    Util.prt(nmod+" of "+nh+" records need writing.");*/
    
    
  }
  
  //public int getSize() {return nh;}
  /** timeoutWrite causes any holdings which have aged at least ms millis to 
   *be update in the database.  Often the deferWrite flags are set for records so
   *that updates do not occur on each change and this routine would then cause the
   *changed records to update periodically
   *@param ms The number of ms that the record must have aged to be updated
   *@param max Number of records to  update as a maximum
   * @return Number of records updated.
   */
  public synchronized int  timeoutWrite(int ms, int max) {
    state=8;
    int recs=0;
    synchronized(chans) {
      TLongObjectIterator<ArrayList<HoldingDay>> itr = chans.iterator();
      while(itr.hasNext()) {
        itr.advance();
        ArrayList<HoldingDay> holdings= itr.value();
        for (HoldingDay holding : holdings) {
          holding.doUpdate(ms);
        }
      }
    }  
    /*for(int i=0; i<nh; i++) 
      if(h[i].doUpdate(ms)) {
        recs++;
        if(recs >= max) return recs;
      } */
    state=0;
    return recs;
  }
  /*private void drop(int i) {
    if(i < h.length-1) {
       h[i].close();
       System.arraycopy(h, i+1, h, i, h.length-i-1);
     }
     h[h.length-1]=hempty;
     nh--; 
  }*/
    /** keepWriting causes any holdings which have aged at least sec seconds to 
   *be update in the database.  Often the deferWrite flags are set for records so
   *that updates do not occur on each change and this routine would then cause the
   *changed records to update periodically.  A maximum of max records will be update
   * on each call.  The starting point proceeds through the array so all records will
   * eventually be written.
   *@param sec The number of seconds that the record must have aged to be updated
   *@param max Number of records to  update as a maximum
   */
  int keepStart=0;
   
  public synchronized int keepWriting(int sec, int max) {
    if(dest == null) 
      dest = new Object[chans.size()*2];
    if(dest.length < chans.size()) 
      dest = new Object[chans.size()*2];
    dest = chans.values();
    state=10;
    int recs=0;
    if(keepStart >= chans.size()) keepStart=0;    // insure the keepStart is in current range
    int i=keepStart+1;
    if(chans.size() == 0) return 0;
    if(i >= chans.size()) i=0;
    int loops=0;
    while(recs < max && i != keepStart) {
      ArrayList<HoldingDay> holdings = (ArrayList<HoldingDay>) dest[i];
      for(HoldingDay holding : holdings) {
        if(holding.getAgeWritten() >= sec && holding.isModified()) {
          boolean updated = holding.doUpdate();
          recs++;
          if(!updated && holding.getOutOfScope()) {
            Util.prta("** found out-of-scope holding - drop it "+holding.toString2());
            holdings.remove(holding);
          }
        }
      }
      if(recs > max) break;
      /*if(h[i] != null) {
        if(h[i].getAgeWritten() >= sec && h[i].isModified()) {
          boolean updated = h[i].doUpdate();
          recs++;
          if(recs >= max) break;
          if(!updated && h[i].getOutOfScope()) {
            Util.prta("** Found out-of-scope holding - drop it "+h[i].toString2());
            drop(i);
            i--;
            if(i < 0) i=0;
          } 
        }
      }  
      else Util.prta("HA:["+tag+"] Exception : keepwriting h[i] is null! nh="+nh+" i="+i+" tag="+tag);*/
      i++; 
      loops++;
      if(i >= chans.size()) i=0;
      if(loops > chans.size()) {
        Util.prta("HA:["+tag+"] Exception: keepwriting is stuck in a loop chans.size="+chans.size()+
          " keepStart="+keepStart+" i="+i+" recs="+recs+" max="+max+" tag="+tag);
        break;
      }
    }
    keepStart=i;
    
    // This insure no one is missed!
    /*if(h[i] == null) Util.prta("HA:["+tag+"]  ***** keepwriting2 h[i] is null! nh="+nh+" i="+i+" tag="+tag);
    else if(h[i].getAgeWritten() >= sec && h[i].isModified()) h[i].doUpdate();*/
    state=0;
    return recs;
  }
  
  /** try to consolidate overlapping Intervals into a single one
   *@param max max number of combines to allow (used to limit processing
   *@return Number of combines done
   */
  public synchronized int consolidate(int max) {
    state=11;
    return consolidate(max, false);
  }
  /**
   * 
   * @param max
   * @param ordered This is not currently used as the ordering of the ArrayList makes no difference.
   * @return 
   */
  public synchronized int consolidate(int max, boolean ordered) {
    if(dest == null) 
      dest = new Object[chans.size()*2];
    if(dest.length < chans.size()) 
      dest = new Object[chans.size()*2];
    dest = chans.values();
    state=12;
    int ncombined=0;
    long ncompares=0;
    boolean update;
    long total=System.currentTimeMillis();
    //long tcompares=0;
    long taddon=0;
    long tmp;
    long started=System.currentTimeMillis();
    int lastStart=-1;
    boolean dbg2=false;
    for(int start = 0; start < chans.size(); start++) {
      try {
        if(start == lastStart) Util.prta("HA:["+tag+"] consolidate not making progress! start="+start);
        lastStart=start;
        ArrayList<HoldingDay> list = (ArrayList<HoldingDay>) dest[start];
        if(list.size() > 500) {
          if(list.size() > 10000) Util.prta("HA: start sort on "+list.size());
          Collections.sort(list);
          if(list.size() > 10000) Util.prta("HA: done sort on "+list.size());
          ordered=true;
        }
        // print progress if this is taking a bit
        if(System.currentTimeMillis() - started > 30000) {
          Util.prta("HA:["+tag+"] cons prog start="+start+"/"+chans.size()+
              " seed="+list.get(0).getSeedName()+" ncomps="+ncompares+
              " tadd="+taddon+" ncomb="+ncombined+" max="+max);
          started=System.currentTimeMillis();
        }

        tmp=System.currentTimeMillis();
        int i=0;
        int j=1;
        while( i<=list.size()-1 && j<list.size()) {
            if( i == j) {
              j++;
              if(j >= list.size()) {i++;j=i+1;}
              continue;
            }
            if(ordered) {
              if(!list.get(i).getType().equals(list.get(j).getType()) || list.get(j).getYRDOY() > list.get(i).getYRDOY() ) {    // does not make sense to go on
                i++;
                j=i+1;
                continue;
              }
            }
            // If the data are ordered, then we can bail if we find a j after the end of i
            ncompares++;
            if(ncompares % 5000000 == 0) Util.prta("HA: ncompares="+ncompares);
            tmp = System.currentTimeMillis();
            update=false;
            if(list.get(i).getYRDOY() == list.get(j).getYRDOY()) {
              try {
                update=list.get(i).addOn(list.get(j).getStart(), list.get(j).getLengthInMillis());
              }
              catch(HoldingDayException e) {
                e.printStackTrace();
              }

              // They overlap, remove the jth one, including in the database
              if(update) {
                if(dbg2) {
                  Util.prt("Combine i="+(i+"   ").substring(0,4)+" j="+(j+"   ").substring(0,4));
                  Util.prt("Combine i="+(i+"   ").substring(0,4)+" j="+(j+"   ").substring(0,4)+" "+list.get(i)+" overlaps");
                  Util.prta("          "+list.get(j));
                }
                list.get(i).doUpdate();      // Update the consolidated one
                list.get(j).delete();        // Remove from database
                list.get(j).close();
                list.remove(j);
                j--;                  // adjust index since we just delete this record
                ncombined++;
                if(ncombined >= max ) {
                  Util.prta("Consolidate early list.size="+list.size()+" ncompares="+ncompares+       
                    " taddon="+taddon+" total="+(System.currentTimeMillis()-total)+" max="+max);
                  state=0;
                  return ncombined;
                }
              }   // if(update)
            }     // if yrdoy match
                 // end for loop on j
            j++;
            if(j >= list.size()) {i++; j=i+1;}
        }         // end for loop on i
        taddon += (System.currentTimeMillis()-tmp);
        if(list.size() > 10000) 
          Util.prta("HA: consolidate done chan "+start+"/"+chans.size()+" list.size()="+list.size()+
                  " ncompares="+ncompares+" taddon="+taddon);
      }
      catch(RuntimeException e) {
        Util.prt("Runtime Exception in HoldingArray.consolidate()) start="+start+" nh="+chans.size()+" "+e);
        Util.prt("RuntimeException = "+e.getMessage());
        e.printStackTrace();
      }
    }     // for each list in chans
    long now = System.currentTimeMillis();
    if(now - total > 100) Util.prta("Consolidate nh="+chans.size()+" ncompares="+ncompares+
          " taddon="+taddon+" total="+(now-total)+" ncomb="+ncombined);
    state=0;
    return ncombined;
  }
  
  /** Purge the list of any that are older than given number of seconds
   *@param age Then age at which we will purge out a record in seconds
   * @return Number of purged records.
   */
  public synchronized int purgeOld(int age) {
    int npurge=0;
    TLongObjectIterator<ArrayList<HoldingDay>> itr = chans.iterator();
    while(itr.hasNext()) {
      itr.advance();
      ArrayList<HoldingDay> holdings= itr.value();
      for (HoldingDay holding : holdings) {
        if(holding.getAge() > age) {
          holding.doUpdate(0);
          holding.close();
          holdings.remove(holding);
        }
        else if(age <= 0) Util.prt("HA:["+tag+"] ***** age 0 weirdly left this record="+holding);
      }
    }
    /*if(h == null) return 0;     // This is closed
    state=13;
    int i=nh-1;
    int npurge=0;
    if(age != 0) Util.prta("HA:["+tag+"]Start purge at i="+i+" age="+age);
    while (i >=0) {
      if(h[i].getAge() >= age) {
        if(age == -1 && i % 500 == 0) Util.prta("HA:["+tag+"] Purge on age "+h[i].getAge()+">="+age+" i="+i+" nh="+nh+" len="+h.length+" "+h[i].toString());
        h[i].doUpdate(0);
        npurge++;
        
        if(i < h.length-1) {
          h[i].close();
          System.arraycopy(h, i+1, h, i, h.length-i-1);
        }
        h[h.length-1]=hempty;
        nh--; 
      } else { 
        if(age <= 0) Util.prt("HA:["+tag+"] ***** age 0 left this record="+h[i].toString2());
      }
      i--;
    }*/
    if(age != 0) Util.prta("HA:["+tag+"] End of npurge="+npurge);
    state=0;
    return npurge;
  }
  public boolean isClosed() {return (chans.size() == 0);}
  public synchronized void close() {
    state=0;
    TLongObjectIterator<ArrayList<HoldingDay>> itr = chans.iterator();
    while(itr.hasNext()) {
      itr.advance();
      ArrayList<HoldingDay> holdings= itr.value();
      for (HoldingDay holding : holdings) {
        holding.doUpdate(0);
        holding.close();
      }
      itr.remove();
      holdings.clear();
    }
    //Util.prta("HA:["+tag+"] release all memory ");
    /*for(int i=0; i<h.length; i++) h[i] = null;  // release all of the array
    h = null;*/
    state=0;

  }
  public static void cleanup() {

    Util.prt(" HoldingArray Cleanup called");
  }
  
  /** test main routine
   * @param args Command line args
   */
  public  static  void  main  (String  []  args)  {
    DBConnectionThread  jcjbl;
    Util.setModeGMT();
    Util.init("edge.prop");
    Connection  C;
    boolean convert=false;
    boolean test=false;
    String [] tables = {"status.holdingshist2","status.holdingshist","status.holdings"};
    User user=new User("dkt");
    GregorianCalendar consolidateStart = new GregorianCalendar();
    consolidateStart.set(1990, 0, 1);
    consolidateStart.setTimeInMillis(consolidateStart.getTimeInMillis()/86400000L*86400000L);
    GregorianCalendar consolidateEnd = new GregorianCalendar();
    consolidateStart.setTimeInMillis(consolidateStart.getTimeInMillis()/86400000L*86400000L+86400000L);    
    boolean consolidate=false;

    if(args.length == 0) {
      args = new String[5];
      args[0] = "-tables";
      args[1] = "status.holdingshist2,status.holdingshist,status.holdings";
      args[2] = "-convert";
      args[3] = "-db";
      args[4] = "localhost/3306:status:mysql:status";
    }

    for(int i=0;  i< args.length; i++) {
      if(args[i].equalsIgnoreCase("-test")) test=true;
      else if(args[i].equalsIgnoreCase("-db")) {Util.setProperty("StatusDBServer",args[i+1]); i++;}
      else if(args[i].equalsIgnoreCase("-convert")) convert=true;
      else if(args[i].equalsIgnoreCase("-tables")) {tables=args[i+1].split(","); i++;}
      else if(args[i].equalsIgnoreCase("-consolidate")) consolidate=true;
      else if(args[i].equalsIgnoreCase("-b")) {consolidateStart.setTimeInMillis(Util.stringToDate2(args[i+1]).getTime()); i++;}
      else if(args[i].equalsIgnoreCase("-e")) {consolidateEnd.setTimeInMillis(Util.stringToDate2(args[i+1]).getTime()); i++;}
    }
    try  {
      jcjbl  =  new  DBConnectionThread(Util.getProperty("StatusDBServer"),"update", "status", true,false,"testHolding", Util.getOutput());
      if(!jcjbl.waitForConnection())
        if(!jcjbl.waitForConnection())
          if(!jcjbl.waitForConnection()) {
        Util.prta("** Could not connect to "+Util.getProperty("StatusDBServer"));
        System.exit(1);
      }
      jcjbl.setTimeout(3600);    // queries may take a long time,disable watchdog for long queries.mss
      HoldingDayArray ha = new HoldingDayArray();
      Statement stmt=jcjbl.getConnection().createStatement(
              ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      if(test) {
        int i=stmt.executeUpdate("DELETE FROM holdings WHERE ID>0");

        GregorianCalendar g = new GregorianCalendar(2005,1,1,10,0,0);
        long base=g.getTimeInMillis();
        ha.addOn("USAAA  BHZ00","AA",base+15000,10000);
        ha.addOn("USAAA  BHZ00","AA",base+25000,10000);
        ha.addOn("USAAA  BHZ00","AA",base+40000,10000);
        ha.addOn("USAAA  BHZ00","AA",base+35000,5000);
        ha.addOn("USBBB  BHZ00","AA",base+30000,10000);
        ha.addOn("USBBB  BHZ00","AA",base+40000,10000);
        ha.addOn("USBBB  BHZ00","AA",base+60000,10000);
        ha.addOn("USBBB  BHZ00","AA",base+50000,10000);
        ha.addOn("USBBB  BHZ00","AA",base+60000, 3*86400000);
        ha.list();
        ha.timeoutWrite(10000,5);
        long was = System.currentTimeMillis();
        while ((System.currentTimeMillis() - was ) < 3020) {Util.sleep(1000);Util.prta(" Waiting");}
        Util.prt("try a consolidation");
        ha.consolidate(5);
        ha.list();
        ha.timeoutWrite(10000,5);
        ha.purgeOld(8);
        System.exit(0);
      }
      if(consolidate) {
        try (ResultSet rs = jcjbl.executeQuery("SELECT * FROM holdingsday ORDER BY yrdoy,seedname,start")) {
          String lastChan=null;
          int nrec=0;
          while(rs.next()) {
            ha.addEnd(rs);
            String seedname = rs.getString("seedname");
            if(lastChan == null) lastChan=seedname;
            if(!lastChan.equals(seedname)) {
              int ncombine = ha.consolidate(Integer.MAX_VALUE);
              ha.close();
              Util.prta(lastChan+" "+nrec+" records in ncombine="+ncombine);
              lastChan=seedname;
              nrec=0;            
            }
          }
        }
        System.exit(0);
      }
      if(convert) {
        long now = System.currentTimeMillis();
        long qtime;
        ha.alwaysAdd();
        long q2time;
        long contime;
        long totqtime=0;
        long totq2time=0;
        long totcontime=0;
        long totnrec=0;
        long totncombine=0;
        long hatime=0;
        long tothatime=0;
        ArrayList<String> chans = new ArrayList<>(2000);
        for(String table: tables) {
          Util.prta("Start query on distinct seednames "+table);
          stmt=jcjbl.getConnection().createStatement(
              ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);          
          ResultSet rschan = stmt.executeQuery("SELECT DISTINCT seedname FROM "+table);
          Util.prta("End query on distinct seednames "+table);
          try {
            while(rschan.next()) {
              chans.add(rschan.getString("seedname"));
            }
            rschan.close();
            stmt.close();
          }
          catch(SQLException e) {
            Util.prt("SQLError getting list of distinct channels");
            e.printStackTrace();
            System.exit(1);
          }
          
          // For each channel request all of the holdings and process into holdingsday
          for(String chan : chans) {
            try {
              qtime = System.currentTimeMillis();
              //Util.prta("Start seedname "+chan);
              String s = "SELECT * FROM "+table+" WHERE seedname='"+chan+"' ORDER BY type,start";
              try (ResultSet rs = jcjbl.executeQuery(s)) {
                int nrec=0;
                qtime = System.currentTimeMillis() - qtime;
                q2time = System.currentTimeMillis();
                hatime=0;
                while(rs.next()) {
                  String seedname = Util.rightPad(rs.getString("seedname"),12).toString();
                  String type = rs.getString("type");
                  long start = rs.getTimestamp("start").getTime()/1000*1000+rs.getInt("start_ms");
                  long ended = rs.getTimestamp("ended").getTime()/1000*1000 + rs.getInt("end_ms");
                  hatime=hatime-System.currentTimeMillis();
                  ha.addOn(seedname, type, start, ended - start);
                  hatime = System.currentTimeMillis() + hatime;
                  nrec++;
                  if(nrec % 1000000 == 0) Util.prta(" *** " +HoldingDay.getHoldingStats());
                }
                q2time = System.currentTimeMillis() - q2time;
                contime = System.currentTimeMillis();
                int ncombine = ha.consolidate(Integer.MAX_VALUE);
                ha.close();
                contime = System.currentTimeMillis() - contime;
                Util.prta(chan+" "+nrec+" records in ncombine="+ncombine+" qtime="+qtime+
                        " q2time="+(q2time-hatime)+" hatime="+hatime+" contime="+contime);
                totqtime+=qtime;
                totq2time+= q2time;
                totcontime+=contime;
                totnrec+=nrec;
                totncombine+=ncombine;
                tothatime += hatime;
              }   // processing resultset
            }
            catch(SQLException e) {
              e.printStackTrace();
            }
          }   // for each channel
          Util.prta("#totrec="+totnrec+" totcomb="+totncombine+" totqtime="+totqtime+
                  " totq2time="+(totq2time-tothatime)+" tothatime="+tothatime+" totcontime="+totcontime);
          Util.prta(HoldingDay.getHoldingStats());
          System.exit(0);
        }   // for each table
      }   // if(convert)
    }
    catch(InstantiationException e) {
      Util.prt("Could not instatiation DB connection");
      e.printStackTrace();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e," Main SQL unhandled=");
      e.printStackTrace();
      System.err.println("SQLException  on  getting test Holdings");
    }
    catch(RuntimeException e) {
      e.printStackTrace();
    }
  }
}
