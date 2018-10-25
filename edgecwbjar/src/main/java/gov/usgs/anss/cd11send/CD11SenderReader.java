/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


/*
 * CD11SenderReader.java
 *
 * Created on March 25, 2006, 1:32 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.cd11send;

import gov.usgs.anss.cd11.CD11Frame;
import gov.usgs.anss.cd11.GapList;
import gov.usgs.anss.edgemom.CD11SenderClient;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

  
  /** this inner class reads and writes data to a CD1.1 client with the data
   * It needs to handle all DataFrame, CommandFrames, and send out AckNacks
   */
  public class CD11SenderReader extends Thread {
    private Socket sd;
    private boolean terminate;
    private int nframes;                  // count the input frames
    private final CD11Frame frm;
    private final CD11Frame outFrame;           // and return connection response frames.
    private final String tag2;
    private final String station;
    private int state;
    private final CD11SenderClient par;
    private final boolean dbg=false;
    private GapList inGaps;
    // For the ack packet, scratch space
    private byte [] b = new byte[10000];
    private final ByteBuffer bb;
    private final byte [] set = new byte[20];   // for getting the frame set
    private final ByteBuffer setbb;
    // These variables come from the last cracking of an Ack Frame
    private long lastAcked;               // The highest sequence number acked
    private long lowAcked;                // The low end of ack range per last ack packet
    private long [] lows = new long[100];
    private long [] highs = new long[100];
    private int ngaps;
    private final StringBuilder tmpsb = new StringBuilder(20);
    private final StringBuilder stat = new StringBuilder(20);

    @Override
    public String toString() {return toStringBuilder(null).toString();}
    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if(sb == null) {
        sb=Util.clear(tmpsb);
      }
      synchronized(sb) {
        sb.append(tag2).append(" #fr=").append(nframes);
      }
      return sb;
    }    
    public void terminate() {terminate=true; if(sd != null) try {sd.close();} catch(IOException e){}}
    public int getNframes() {return nframes;}
    public int getStateInt() {return state;}
    public long getLastAckSeq() {return lastAcked;}
    public long getFirstAckSeq() {return lowAcked;}
    /** This class reads and writes data on an open socket to a CD1.1 data socket.  It gets the connection from
     * the parent CD11SenderReader.
     * @param s The socket on which to perform data I/O 
     * @param stat The station name for tags
     * @param parent the edgethread to use for logging
     */
    public CD11SenderReader(Socket s, String stat, CD11SenderClient parent) {
      sd = s;
      par = parent;
      frm = new CD11Frame(10000, 100, par);

      station=stat;
      Util.clear(this.stat).append(station);
      terminate=false;
      tag2 = "CD11SRdr:"+stat+"["+this.getId()+"]";
      outFrame = new CD11Frame(10000, 100, this.stat, Util.clear(tmpsb).append("0"), 0, -1, 0, par);
      bb = ByteBuffer.wrap(b);
      setbb = ByteBuffer.wrap(set);
      par.prta("new ThreadProcess "+getName()+" "+getClass().getSimpleName()+"@"+Util.toHex(hashCode())+
              " frm@"+Util.toHex(frm.hashCode())+" ofrm@"+Util.toHex(outFrame.hashCode())+"  tag="+tag2+" l="+stat);
      start();
    }
    public void newSocket(Socket ss) {
      par.prta(tag2+" * Setting socket to "+ss);
      if(ss == null) new RuntimeException("Back trace set to null").printStackTrace(par.getPrintStream());
      if(ss != null) sd = ss;
    }
    public void doReadFrame() throws IOException {
      long now=0;
      int len;
      //long lo=0;
      //long hi=0;
      // This section is weird.  We were getting null pointers on the call to available(), this is an attempt to
      // prevent that from happening and to log it.
      while (sd == null && !terminate) try{sleep(500);} catch(InterruptedException e) {}
      if(sd.getInputStream() == null) {
        try{sd.close();}catch(IOException e) {}
        throw new IOException("**** Socket apparently closed!");
      }
      try{
        while(sd.getInputStream().available() <= 0 && !terminate) {
          try{sleep(500);} catch(InterruptedException e) {}
        }
      }
      catch(RuntimeException e) {
         par.prta(Util.clear(tmpsb).append(" Runtime error sd=").append(sd));
         e.printStackTrace(par.getPrintStream());
         if(sd != null)
           if(sd.getInputStream() == null) par.prta("Runtime sd.getInputStream is null");
           else par.prta("Runtime sd.inputStream ="+sd.getInputStream());
         throw new IOException("*** Socket apparently closed sd="+sd);
      }
      //par.prta("Ack RCV Read frame avail="+sd.getInputStream().available());
      len = frm.readFrame(sd.getInputStream(), null);  // to dump debug, set 2nd argument to par
      if(terminate) return;
      state=3;
      if(len == 0) {terminate=true; par.prta(Util.clear(tmpsb).append(tag2).append("GOT EOF ON socket--- exit!")); return;}
      state=2;
      if(dbg) par.prta(Util.clear(tmpsb).append(tag2).append(" RCV ").append(frm.toStringBuilder(null)));
      nframes++;
      if(frm.getType() == CD11Frame.TYPE_ACKNACK) {}
      else if(frm.getType() == CD11Frame.TYPE_ALERT) {
        par.prta(Util.clear(tmpsb).append(tag2).append(" Alert frame.  close down this link"));
        terminate=true;
        return;
      }
      else {
        par.prta(Util.clear(tmpsb).append(tag2).append(" Got unexpected Frame type ").append(frm.getType()));
      }

      // Handle the various frame types
      if(frm.getType() == CD11Frame.TYPE_ACKNACK) {
        now = System.currentTimeMillis();
        state=4;
        if(frm.getBodyLength() > b.length) {
          par.prta(Util.clear(tmpsb).append(tag2).append(" **** expand buffer for LONG ack! frm.len=").append(frm.getBodyLength()));
          b = new byte[frm.getBodyLength()*2];
        }
        System.arraycopy(frm.getBody(), 0, b, 0, frm.getBodyLength());
        bb.position(0);
        bb.get(set);
        for(int i=0; i<20; i++) if (set[i] == 0) set[i] = 32;
        String frmset = new String(set).trim();
        lowAcked = bb.getLong();
        lastAcked = bb.getLong();

        ngaps=bb.getInt();
        par.prta(Util.clear(tmpsb).append(tag2).append("Ack RCV frmset=").append(Util.toAllPrintable(frmset)).
                append(" low=").append(lowAcked).append(" high=").append(lastAcked).append(" #gaps=").append(ngaps));
        if(ngaps > lows.length) {
          par.prt(Util.clear(tmpsb).append(tag2).append("Ack RCV increase gap arrays to ").append(ngaps*2));
          lows = new long[ngaps*2];
          highs = new long[ngaps*2];
        }
        for(int i=0; i<ngaps; i++) { lows[i] = bb.getLong(); highs[i]=bb.getLong();}
        if(dbg) for(int i=0; i<ngaps; i++) par.prt(Util.clear(tmpsb).append(lows[i]).
                append("-").append(highs[i]).append(" ").append(highs[i]-lows[i]).append(" ").
                append(lastAcked - lows[i]));
      }
    }
    @Override
    public void run() {
      long lastProcessed=System.currentTimeMillis();
      long lowSeq;
      long highSeq;
      long now;
      int gapDump=0;
      int gapNumber;
      int maxsecs=600;
      StringBuilder runsb = new StringBuilder(50);
      while(!terminate) {
        try {
          while(sd == null || sd.isClosed() || !par.isConnected()) {
            try{sleep(20000);} catch(InterruptedException e) {}
          }
          state=1;
          // read in a frame, hopefully an Ack
          doReadFrame();
          if(terminate) break;
          if(frm.getType() == CD11Frame.TYPE_ACKNACK) {
            now = System.currentTimeMillis();
            if(now - lastProcessed < 118000) {
              par.prta(Util.clear(runsb).append(tag2).append("Ack RCV too soon to process. ").append((now-lastProcessed)/1000)); 
              continue;
            }
            lastProcessed = now;
            if(inGaps == null) {    // First time, build initial frame set
              inGaps = new GapList(0, lowAcked, par.getGapFilename()+"in", par);
              inGaps.setDebug(dbg);
            }
            inGaps.rcvGapSet(lowAcked, lastAcked, ngaps, lows, highs, station);// Update frameset
            int nfree = inGaps.trimList(inGaps.getHighestSeq() - 10*8600);  // Limit fetches to 10 days ago
            gapNumber = 100000;
            int ntotsent = 0;
            int npack=0;
            if(inGaps.getGapCount() > 0) par.prt(inGaps.toStringBuilder(runsb).insert(0, "InGaps : "));// Show incoming gap list

            while(inGaps.getGapCount() != 0 ) {
              if(gapNumber < 0) break;
              if(gapNumber >= inGaps.getGapCount()) gapNumber=inGaps.getGapCount()-1;// reset to oldest gap
              //par.prta(tag2+"Ack RCV consider "+gapNumber+" "+inGaps.getLowSeq(gapNumber)+"-"+inGaps.getHighSeq(gapNumber)+" "+(now - inGaps.getLastTime(gapNumber))/1000);
              int nsent = 0;      // counter of segments sent for this gap
              // If the wait between interval has expired, do the thing (basically #days to gap * 10 minutes with upper limit of 1 day)
              if(now- inGaps.getLastTime(gapNumber) > Math.min(86400000L, ((lastAcked - inGaps.getLowSeq(gapNumber))/8640+1)*maxsecs*1000)) { // If its not too soon to consider this gap again
                lowSeq = inGaps.getLowSeq(gapNumber);
                highSeq = inGaps.getHighSeq(gapNumber);
                long del = (now - inGaps.getLastTime(gapNumber))/1000;
                inGaps.setLastTime(gapNumber, now); //}  // Nothing happpened on this gap, so start new timeout
                while(lowSeq < highSeq /*&& (ntotsent + nsent) < 60*/) {
                  npack = par.doFetchForGap(lowSeq, highSeq, inGaps);
                  par.prta(Util.clear(runsb).append(tag2).append("Ack RCV do gap ").append(lowSeq).append("-").
                          append(highSeq).append(" #=").append(gapNumber).append("/").append(inGaps.getGapCount()).
                          append(Util.ascdatetime2(lowSeq*10000L+CD11Frame.FIDUCIAL_START_MS)).append("-").
                          append(Util.ascdatetime2(highSeq*10000L+CD11Frame.FIDUCIAL_START_MS)).append(" #free=").append(nfree).
                          append(" del=").append(del).append(" #pktsent=").append(npack));
                   if(npack < 0 || terminate) {
                    par.prt(Util.clear(runsb).append(tag2).append("Ack RCV too soon or terminate to do gaps fill!")); 
                    break;
                  }
                  nsent += npack;
                  par.prt(Util.clear(runsb).append(tag2).append("Ack RCV #success=").append(nsent).append(" #pack=").append(npack));
                  if(System.currentTimeMillis() - now > 300000) {
                    par.prta(Util.clear(runsb).append(tag2).append("Ack RCV 300 seconds. avail=").
                            append(sd.getInputStream().available()));
                    break;
                  }
                  lowSeq += 60;
                  while(sd.getInputStream().available() > 10) doReadFrame();  // read in any frames received while processing the last segment
                }
                if(terminate) break;
                // If warming up, or we have been running on these gaps for enought time, clear the loop and get a revised frameset
                if(npack < 0 || System.currentTimeMillis() - now > 300000 || terminate) {lastProcessed = System.currentTimeMillis();break;}
                ntotsent += nsent;
                //if(nsent == 0)  {
              }
              gapNumber--;
              //par.prta(tag2+"Gap bottom gap#="+gapNumber);
              //try{sleep(2000);} catch(InterruptedException e) {}
            }
            inGaps.writeGaps(false);
            
          }
          else if(frm.getType() == CD11Frame.TYPE_ALERT) {
            par.prta(Util.clear(runsb).append(tag2).append(" Alert frame.  close down this link"));
            terminate=true;
            break;
          }

          else {
            par.prta(Util.clear(runsb).append(tag2).append(" Got unexpected frame type=").append(frm.getType()));
            //frm.sendAlert("Link unexpected type");
          }
          if(nframes % 1000 == 0) par.prta(toStringBuilder(null));
        }
        catch(IOException e) {
          Util.SocketIOErrorPrint(e, tag2+" getting streams or reading", par.getPrintStream());
          par.prta(Util.clear(runsb).append(tag2).append(" main look exit"));
          if(!sd.isClosed()) {
            try{sd.close();} catch(IOException e2) {}
          }
        }
        catch(RuntimeException e) {
          par.prta(Util.clear(runsb).append(tag2).append("Got RuntimeError e=").append(e));
          e.printStackTrace(par.getPrintStream());
          e.printStackTrace();
          par.prta(Util.clear(runsb).append(tag2).append(" Runtime handled.  Go to top of loop!"));
        }

      } // while ! terminated
 
      par.prta(Util.clear(runsb).append(tag2).append(" has exited"));
      
    }


}
