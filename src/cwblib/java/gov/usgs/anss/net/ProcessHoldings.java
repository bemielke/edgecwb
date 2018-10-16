/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * ProcessHoldings.java
 *
 * Created on May 9, 2005, 3:55 PM
 */

package gov.usgs.anss.net;
import gov.usgs.alarm.SendEvent;
import java.util.*;
import gov.usgs.anss.util.*;


/**
 *
 * @author davidketchum
 */
public class ProcessHoldings extends Thread {

  PBuf [] p ;
  private int MAX_UDP=50000;
  int nextin,nextout,highwater;
  int nused;
  int msgLength=26;
  HoldingArray ha;
  GregorianCalendar now;
  boolean terminate=false;
  long totalProcessed;
  long overflow;
  int expected=-1;
  String tag;
  int statertn;
  
  // Local variables;
  int time2=0;
  int time3=0;
  boolean dbg=false;
  public int getStatertn() {return statertn;}
  public void setDebug(boolean t) {dbg=t;}
  public void setTag(String s) {tag=s;}
  public ProcessHoldings(int max, HoldingArray h) {
    statertn=1;
    MAX_UDP=max;
    ha=h;
    terminate=false;
    tag="";
    totalProcessed=0;
    p = new PBuf[MAX_UDP];
    for(int i=0; i<MAX_UDP; i++) {
      p[i]= new PBuf(msgLength);
    }
    now = new GregorianCalendar();
    nextin=0;
    nextout=0;
    start();
    statertn=0;
  }
  
  @Override
  public void run() {
    statertn=2;
    byte [] buf = new byte[100];
    Util.prta(tag+"PH: started with max="+MAX_UDP);
    while(!terminate) {
      try {
        if(ha.isClosed()) {terminate=true; break;}
        statertn=3;
        processAll();
        statertn=4;
        try{ sleep(100);} catch (InterruptedException e) {}
      }
      catch(java.lang.OutOfMemoryError e) {
        Util.prt("OutOfMemory - exit!");
        e.printStackTrace();
        SendEvent.edgeEvent("OutOfMemory", "TcpHoldings/process holdings - exit! ", "ProcessHoldings");
        System.exit(1);
      }
      catch(RuntimeException e) {
        Util.prta("Error on ProcessHoldings e="+e);
        e.printStackTrace();
      }
    }       // infinite while(true)
    statertn=5;
    Util.prta(tag+"PH: Final Process all called terminated!!!"+nused);
    if(!ha.isClosed()) {
      processAll();
      statertn=6;
      processAll();
    }
    statertn=7;
    Util.prta(tag+"PH: run() terminated!!! "+nused+" total processed="+totalProcessed);
  }
  public long getTotalProcessed(){return totalProcessed;}
  public long getOverflow(){return overflow;}
  public void setTerminate() {terminate=true;}
  public void processAll() {
    long top;
    //GregorianCalendar now;
    byte [] b;
    statertn=9;
    int yymmdd;
    int msecs;
    String chan;
    if(dbg) Util.prta("PH: PA in="+nextin+" out="+nextout);
    while(nextout != nextin) {
      if(ha.isClosed()) break;    // some one closed it, we need to leave!
      statertn=10;
      top = System.currentTimeMillis();

      // Unpack the data into java args
      b = p[nextout].getData();
      // so nextout in particular is not in transit during a queue operation
      statertn=11;
      synchronized(this) {
        statertn=12;
        totalProcessed++;
        nextout++;
        if(nextout == MAX_UDP) nextout=0;
        nused=nextin-nextout;
        if(nused < 0) nused+=MAX_UDP;
      }
      statertn=13;
      int len = b.length;
      if(len < msgLength) Util.prt(tag+"PH: HoldingArray too short="+len);

     /* the raw bytes are :
       * 0 int yymmdd
       * 4 int ms since midnight
       * 8 int ms length of packet
       * 12 char*12 SCNL (seed name NNSSSSSCCCLL)
       * 24 char*2 type (the key for distinquishing different runs or data "places"
       */
      if(!FUtil.stringFromArray(b,12,2).equals("ZZ") && Util.isValidSeedName(FUtil.stringFromArray(b,12,12))) { // throw this network away!
        yymmdd = FUtil.intFromArray(b, 0);
        msecs = FUtil.intFromArray(b,4);
        long ms = Util.toGregorian2(yymmdd, msecs);
        /*int yr = yymmdd/10000;
        if(yr < 100) yr = yr + 2000;
        yymmdd = yymmdd-yr*10000;
        int mon = yymmdd/100;
        int day = yymmdd % 100;
        int hr = msecs /3600000;
        msecs = msecs - hr*3600000;
        int min = msecs / 60000;
        msecs = msecs - min * 60000;
        int secs = msecs/1000;
        msecs =msecs - secs*1000;
        if(yr < 1950 || yr > 2200 || mon<=0 || mon > 12 || hr<0 || hr >23 || min <0 || min >59
            || secs < 0 || secs > 59 || msecs < 0 || msecs > 999) {
          Util.prta("toGregorian data out of range yr="+yr+
              " mon="+mon+" day="+day+" "+hr+":"+min+":"+secs+"."+msecs);
        }*/
        statertn=14;
        //now.set(yr,mon-1,day,hr,min,secs);
        now.setTimeInMillis(ms); // added Apr 2010 
        //now.add(Calendar.MILLISECOND, msecs);
        /*if(FUtil.stringFromArray(b,12,10).equals("USISCO BHZ")) {
          Util.prt("    "+nextout+" "+FUtil.stringFromArray(b,12,12)+" "+FUtil.stringFromArray(b,24,2)+
              " yymmdd="+yymmdd+" "+Util.yymmddFromGregorian(now)+         
              " msec="+msecs+" "+Util.ascdate(now)+" "+Util.asctime2(now)+" "+FUtil.intFromArray(b,8));
          if(expected != -1 && expected != msecs) Util.prt("   **** gap of "+(expected-msecs));
          expected = msecs+FUtil.intFromArray(b,8);
        }*/
        chan = FUtil.stringFromArray(b,19,3);
        if(!(chan.equals("ACE") || chan.equals("ACD") || chan.equals("AFP"))) {   // skip these type
          if(dbg) 
            time2=(int) (System.currentTimeMillis() - top);
          statertn=16;
          ha.addOn(FUtil.stringFromArray(b, 12, 12), FUtil.stringFromArray(b, 24,2), 
                  now.getTimeInMillis() , FUtil.intFromArray(b,8)); 
          statertn=17;
          if(dbg) {
            time3=(int) (System.currentTimeMillis() - top);
            Util.prta("PH: nextout="+nextout+" high="+highwater+" "+time2+" "+time3+" "+FUtil.stringFromArray(b,12,12));
          }
        }
      }
      statertn=18;
      //if(totalProcessed % 100 == 0) yield();   // let someone else have a chance
      statertn=19;
      if(dbg) Util.prta("PH: PA end in="+nextin+" out="+nextout);
    }     // while nextout != nextin
    statertn=20;
  }
  
  public synchronized void queue(byte [] buf) {
    //synchronized (p) {
    //Util.prt("Que at nexting="+nextin+" len="+buf.length);
    if(terminate) Util.prt(tag+"PH: call to Queue during terminate.");
    p[nextin].setData(buf);
    nextin++;
    if(nextin == MAX_UDP) nextin=0;
    if(nextin == nextout) {
      if(overflow % 500 == 0) Util.prta(Util.ascdate()+tag+
          "   ***** PH:overflow UDP buffers "+nextin+" overflow="+overflow+
          " used="+nused+" tot="+totalProcessed);
      overflow++;
      nextin--;
      if(nextin < 0) nextin = MAX_UDP-1;
    }
    //}
    // Point to next buffer and check for buffer overruns
    nused=nextin-nextout;
    if(nused < 0) nused+=MAX_UDP;
    if(nused > highwater) {
      highwater=nused;
      if(highwater%1000 == 0) Util.prta(tag+"PH: New Highwater="+highwater);
    }
  }
  
  public int getNused() {return nused % MAX_UDP;}
  public int getHighwater() {return highwater;}
  public void setHighwater(int h) {highwater=h;}
  public int getMsgLength() {return msgLength;}

  public int getNextin() {return nextin;}
  public int getNextout(){return nextout;}
  
  private class PBuf {
    byte [] buf;
    public PBuf(int length) {
      buf = new byte[length];
      
    }
    public void setData(byte [] b) {
      
      if(b.length > 0) System.arraycopy(b,0,buf,0,Math.max(b.length,buf.length));
    }
    public byte [] getData() {
      return buf;
    }
  }
}

  
