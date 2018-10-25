/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import com.oregondsp.signalProcessing.filter.iir.IIRFilter;
import com.oregondsp.signalProcessing.filter.iir.Butterworth;
import com.oregondsp.signalProcessing.filter.iir.PassbandType;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.cwbquery.QuerySpan;
import gov.usgs.cwbquery.QuerySpanCollection;
import gov.usgs.cwbquery.QuerySpanThread;
import java.io.IOException;
import java.io.OutputStream;
import static java.lang.Thread.sleep;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * This class implements heli filtering in a winston wave server sense. We never
 * know what data will be asked for, and the algorithm may well send us more
 * data than we need and so we do not want to compute it again. It uses various
 * algorithms to based on digit irate. 1) irate >=10 decimate to 10 Hz and apply
 * the HeliFilt
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class HeliFilterIIR {

  // This buffering is shared between all HeliFilters to do the actual work, any processing
  // Should syncronize on object mutex
  private static final int MAX_CACHE = 720;
  private static final Integer mutex = Util.nextMutex();
  private static byte[] helibyte = new byte[2];
  private static ByteBuffer helibb;
  private static int[] df = new int[2];
  private static final byte[] buf = new byte[40];   // temp buf for outputing strings 
  private static final MiniSeedPool msp = new MiniSeedPool();
  private static QuerySpan ourspan;
  private QuerySpan qscspan;
  private double lastTimeOut2000;
  private int[] old;     // This is any leftover data from the last past
  private int[][] oldbhz;
  private boolean dbg = false;
  private IIRFilter filter;
  private IIRFilter filterAll;
  //private IIRFilter lpfilt;
  private long lastMillisAt;
  private final GregorianCalendar g = new GregorianCalendar();
  private final String ttag;
  private int irate;
  private final String channel;
  private String dbgChannel = "ZZZZZZ ZZZ00";
  private final StringBuilder channelsb = new StringBuilder(12);
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final String ip;
  private final EdgeThread par;
  private long bytesOut;
  private int nqueries;
  private final StringBuilder statussb = new StringBuilder(200);
  private final ArrayList<MiniSeed> mss = new ArrayList<>(100);

  public long getMemoryUsage() {
    return (msp.getFree() + msp.getUsed()) * 1024 + (ourspan != null ? ourspan.getMemoryUsage() : 0) + df.length * 4 + helibyte.length;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public void setDebugChannel(String c) {
    dbgChannel = c;
  }

  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    statussb.append("Heli:").append(ttag).append(" #q=").append(nqueries).append(" #bout=").append(bytesOut).
            append(" df.len=").append(df.length).
            append(" helibuf=").append(helibyte.length).append(" msp=").append(msp).
            append(" span=").append((ourspan != null ? ourspan.getMemoryUsage() : 0))
            .append(" totmem=").
            append(Util.df22(((msp.getFree() + msp.getUsed()) * 1024 + (ourspan != null ? ourspan.getMemoryUsage() : 0) + df.length * 4 + helibyte.length) / 1000000.)).append("mB");
    nqueries = 0;
    bytesOut = 0;
    return statussb;
  }

  public HeliFilterIIR(StringBuilder channel, String ip, EdgeThread par) throws IOException {
    this.par = par;
    this.channel = channel.toString();
    Util.clear(channelsb).append(channel);
    this.ip = ip;
    ttag = "HF[" + this.channel + ":" + this.ip + "]";

  }

  /**
   * This is the main way to query data when it is needed. The
   * ArrayList<MiniSeed>
   * contains data which came from disk based queries and the
   * ArrayList<TraceBuf> contains data which came from the QuerySpan (RAM based)
   * data. They may overlap so the user need to trim any MiniSeed blocks the the
   * start time of the first TraceBuf. The user must also call freeBlocks(mss,
   * tsb) when done with the results to free these blocks from their respective
   * pools.
   *
   * @param mss An ArryList<MiniSeed> to receive data blocks from a query to
   * disk
   * @param tsb An ArrayList<TraceBuf> to contain blocks from the QuerySpan
   * memory based buffers
   */
  /* private QuerySpan queryToSpan(String host, GregorianCalendar g, double duration) {
      
      // Save the start time, and duration so this can be split up into two requests (disk and RAM)
      long st = g.getTimeInMillis();
      long endat = st + (long) (duration*1000.+1000.001);   // set end time for duration + 1 second to be sure
      if(qscspan != null) {
        // See if request is entirely in the ram buffer
        if(g.getTimeInMillis() >= qscspan.getMillisAt(0)) return qscspan;
      }
      
      if(ourspan == null) 
        ourspan = new QuerySpan(host, 0, channel, st, duration, 0.99*duration, 0., par);  
      // If we have a qscspan, then use it to get the data blocks
      // get the miniseed blocks from either a remote server on the local QueryMom based one
      // If this is a sizeable request, make sure there is pool space
      if( endat - st > 1000000) {
        while(msp.getUsedList().size() > 100000) {
          par.prta(ttag+" ** HF: queryToSpan wait for space on local MiniSeed Pool in queryToSpan"+
                  msp.getUsedList().size());
          try {sleep(4000);} catch(InterruptedException e) {}
        }
      }
      par.prta(ttag+"queryToSpan: call span.doQuery dur="+(endat-st)/1000.);
      mss.clear();
      ourspan.doQuery(st, (endat - st)/1000., mss);
      par.prta(ttag+"queryToSpan: call span.doQuery ret mss.size()="+mss.size());
      if(dbg )
        par.prta(ttag+"queryToSpan "+Util.ascdatetime2(st)+" dur="+duration+" return="+
              (mss == null?" null":mss.size()));
      if(mss == null) return null;
      if(mss.isEmpty()) return null;

      ourspan.refill(mss, g, duration);
      ourspan.freeBlocks(mss);
      return ourspan;
    }*/

  /**
   * do not allow two calls to this even if users are nuts and hammering it
   *
   * @param requestID The WS request id
   * @param command should always be GETSCNLHELIRAW
   * @param word The array of stuff after the command on the command line
   * @param host The host for the CWB Query
   * @param out Output pipe to caller
   * @return Number of points returned
   * @throws IOException if one is thrown
   */
  public synchronized int doSCNLHeliRaw(StringBuilder requestID, StringBuilder command, StringBuilder[] word,
          String host, OutputStream out) throws IOException {
    //state=7;
    //out.write(requestID.getBytes());

    // This is a data fetch, parse out the start,end make a command line for CWBQuery and run the query
    // word [0] station, word[1] channel, word[2] network, word[3] location,
    //word[4] start time, word[5] endtime, word[6] fill string 
    //String seedname = (word[2]+"  ").substring(0,2)+(word[0]+"     ").substring(0,5)+
    //        (word[1]+"   ").substring(0,3)+ (word[3]+"  ").substring(0,2);
    if (channel.equals(dbgChannel)) {
      dbg = true;
    }
    double start = Double.parseDouble(word[4].toString());
    double end = Double.parseDouble(word[5].toString());
    double start2000 = start;
    start = start + CWBWaveServer.year2000sec;
    double iend = (double) (int) end;
    end = end + CWBWaveServer.year2000sec;
    boolean doAll = false;
    //ArrayList<ArrayList<MiniSeed>> mss=null;
    try {
      if (iend < lastTimeOut2000 - 300. || end - start > 10000.) {
        doAll = true;
        par.prta(Util.clear(tmpsb).append(ttag).append(" ask for old last=").
                append(CWBWaveServer.y2kToString(lastTimeOut2000)).append(" start=").
                append(CWBWaveServer.y2kToString(start2000)).append(" end=").
                append(CWBWaveServer.y2kToString((double) iend)).append(" dur=").
                append(end - start));
        if (dbg) {
          par.prta(Util.clear(tmpsb).append(ttag).append(" ** history do all new last").append(CWBWaveServer.y2kToString(lastTimeOut2000)).append(" end=").append(CWBWaveServer.y2kToString((double) iend)).append(" start=").append(CWBWaveServer.y2kToString(start2000)));
        }
        start = start - 120.;
        lastTimeOut2000 = 0.;     // Do not limit stuff
        if (filterAll != null) {
          filterAll.initialize();
        }
      }

      // If the user is looking for data older than 5 minutes before last returned data, limit his query.
      if (lastTimeOut2000 > 0 && start < lastTimeOut2000 + CWBWaveServer.year2000sec - 300) {
        if (dbg) {
          par.prta(Util.clear(tmpsb).append(ttag).append(" ** Override time to last time out diff=").
                  append(start - lastTimeOut2000 - CWBWaveServer.year2000sec).append(" ").
                  append(Util.ascdatetime2((long) (start * 1000.))).append(" to ").
                  append(Util.ascdatetime2((long) ((lastTimeOut2000 + CWBWaveServer.year2000sec) * 1000. - 300.))));
        }
        start = lastTimeOut2000 + CWBWaveServer.year2000sec;
      }
      if (lastTimeOut2000 <= 0.) {    // If we are just starting, make sure we get data before first request
        start -= 10;      // get some data before where we need it
      }
      // If they are asking after the last time out, lengthen the query to include the last time out
      if (lastTimeOut2000 > 0 && start >= lastTimeOut2000 + CWBWaveServer.year2000sec) {
        start = lastTimeOut2000 + CWBWaveServer.year2000sec - 2.;
      }
      g.setTimeInMillis((long) (start * 1000.));
      double duration = end - start;
      if (duration < 10) {
        duration = 10;
      }
      if (duration > 92000.) {
        duration = 92000.;
      }
      if (dbg) {
        par.prta(Util.clear(tmpsb).append(ttag).append(" Input query modified ").
                append(Util.ascdatetime2(start * 1000.)).append(" to ").
                append(Util.ascdatetime2(end * 1000.)).append(" dur=").append(duration));
      }

      // See if we are part of a Ram Based QueryMom - if so, find the QuerySpan maintained in QuerySpanCollection
      if (qscspan == null) {
        QuerySpanThread qscthr = QuerySpanCollection.getQuerySpanThr(channelsb);
        if (qscthr != null) {
          qscspan = qscthr.getQuerySpan();
        }
        if (dbg) {
          par.prta(Util.clear(tmpsb).append(ttag).append(channel).append("| span=").append(qscspan));
        }
        if (qscspan != null && qscthr != null) {
          if (!qscthr.isReady()) {
            for (int i = 0; i < 300; i++) {
              if (qscthr.isReady()) {
                break;
              }
              try {
                sleep(100);
              } catch (InterruptedException expected) {
              }
              if (i % 100 == 99) {
                par.prta(Util.clear(tmpsb).append(ttag).
                        append("* waiting for span to be ready! ").append(i));
              }
            }
          }
        }
      }
      QuerySpan span = null;
      nqueries++;
      if (qscspan != null) {
        // See if request is entirely in the ram buffer
        if (g.getTimeInMillis() >= qscspan.getMillisAt(0)) {
          span = qscspan;
        }
      }
      synchronized (mutex) {   // NOTE: this single threads all HeliFilerIIR for all disk based queries and processing
        //if(ourspan != null) 
        //if(ourspan.getDuration() > duration * 2 && ourspan.getDuration() > 600.) {
        //  par.prta(ttag+" ** shorten ourspan to "+duration*2+" was "+ourspan.getDuration());
        //  ourspan.resetDuration(Math.max(600.,duration*2));
        //}        
        if (span == null) {    // Do we have to query the disk?
          // This will return the span which will contain some of the data - it might be a QuerySpanCollection or the static span
          // Save the start time, and duration so this can be split up into two requests (disk and RAM)
          long st = g.getTimeInMillis();
          long endat = st + (long) (duration * 1000. + 1000.001);   // set end time for duration + 1 second to be sure
          // If we have a qscspan, then use it to get the data blocks
          // get the miniseed blocks from either a remote server on the local QueryMom based one
          // If this is a sizeable request, make sure there is pool space
          if (endat - st > 1000000) {
            while (msp.getUsedList().size() > 100000) {
              par.prta(Util.clear(tmpsb).append(ttag).
                      append(" ** HF: queryToSpan wait for space on local MiniSeed Pool ").
                      append(msp.getUsedList().size()));
              try {
                sleep(4000);
              } catch (InterruptedException expected) {
              }
            }
          }
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append("queryToSpan: ").append(channel).
                    append(" call doQuery() ").append(Util.ascdatetime2(st)).append(" dur=").append((endat - st) / 1000.));
          }
          mss.clear();
          if (ourspan == null) // First time create ourspan
          {
            ourspan = new QuerySpan(host, 0, channel, st, duration, 0.99 * duration, 0., par);
          }
          ourspan.doQuery(channel, st, (endat - st) / 1000., mss);
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append("queryToSpan ").
                    append(Util.ascdatetime2(st)).append(" dur=").append(duration).
                    append(" return=").append(mss == null ? " null" : mss.size()));
          }
          if (mss != null) {
            if (mss.isEmpty()) {
              span = null;
            } else {
              ourspan.refill(mss, g, duration);
              ourspan.freeBlocks(mss);
              span = ourspan;
            }
          }
        }
        if (span == null) {
          Util.clear(tmpsb).append(requestID).append(" 0\n");
          Util.stringBuilderToBuf(tmpsb, buf);
          out.write(buf, 0, tmpsb.length());
          //out.write((requestID+" 0\n").getBytes());
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append(" no data returned for ").
                    append(channel).append(" ").append(Util.ascdatetime2(g)).append(" dur=").
                    append(duration));
          }
          return 0;
        }
        //word[3]= (word[3]+"  ").substring(0,2).replaceAll(" ","-");

        if (!channel.equals(span.getSeedname())) {
          par.prta(Util.clear(tmpsb).append("Channels do not agree ").append(channel).
                  append(" ").append(span.getSeedname()));
        }

        if (filter == null) {
          irate = (int) (span.getRate() + 0.499);
          if (irate <= 1) {
            filter = new Butterworth(4, PassbandType.BANDPASS, 0.02, 0.08, 1. / irate);
            filterAll = new Butterworth(4, PassbandType.BANDPASS, 0.02, 0.08, 1. / irate);
          } else {
            //filter  = new Butterworth(4, PassbandType.BANDPASS, 1., 0.25*irate, 1./irate);
            //filterAll  = new Butterworth(4, PassbandType.BANDPASS, 1., 0.25*irate, 1./irate);
            filter = new Butterworth(4, PassbandType.HIGHPASS, 1., 0.25 * irate, 1. / irate);
            filterAll = new Butterworth(4, PassbandType.HIGHPASS, 1., 0.25 * irate, 1. / irate);
          }

          old = new int[Math.max(irate * 2, 20)];
          oldbhz = new int[3][MAX_CACHE];
          for (int i = 0; i < MAX_CACHE; i++) {
            oldbhz[2][i] = 2147000000;
          }
          doAll = false;
        }
        // For SCN, we are going to decode each block and write out the timeseries as ascii
        //  par.prta(ttag+" "+blks.get(indexMS).getSeedName()+" Span="+span);
        //else 
        // The output from here is a 1 sps with a mininum an maximum for each time interval with data
        // Times are in year2000 offset time values
        int nsec = (int) (span.getNsamp() / span.getRate() + 2.); // most seconds to be returned from this data
        if (nsec * 24 > helibyte.length) {
          helibyte = new byte[nsec * 36];        // Make sure our internal buffer is at least this big
          helibb = ByteBuffer.wrap(helibyte);
        }

        //int off= (int) (span.getMillisAt(0) %  1000L)/((int) (1000/span.getRate()));// first sample in a new second
        int[] data = span.getData();
        helibb.position(4);
        // Time of the first whole second in the time span
        double time = ((span.getMillisAt(0) + 1999) / 1000 * 1000) / 1000. - CWBWaveServer.year2000sec;
        int diff;
        int max = 0;
        int oldmin = Integer.MAX_VALUE;
        int oldmax = Integer.MIN_VALUE;

        //  1 Hz data
        int ncached = 0;
        if (irate <= 1) {      // We are just going to display it at it irate
          if (df.length < data.length) {
            df = new int[data.length * 3 / 2];
          }
          if (lastTimeOut2000 <= 0.) {   // First blocks, set up preevent stuff
            lastTimeOut2000 = time - 1.;     // This is the first whole second
            //last1sec=data[0];
            for (int i = 0; i < 10; i++) {
              old[i] = data[0]; // fake up the prior 10 seconds
            }
          }
          // if the request is for data before the last out, try to satisfy it from the cache
          if (start2000 < lastTimeOut2000) {
            if (start2000 < lastTimeOut2000 - MAX_CACHE - 1) {
              start2000 = lastTimeOut2000 - MAX_CACHE - 1.;
            }
            while (start2000 <= lastTimeOut2000) {
              int index = ((int) (start2000 + 0.001)) % MAX_CACHE;
              //if(dbg) 
              //  par.prt(start2000+" "+oldbhz[0][index]+" cache="+CWBWaveServer.y2kToString(start2000)+" "+oldbhz[1][index]+" "+oldbhz[2][index]);
              if ((int) (start2000 + 0.001) == oldbhz[0][index] && oldbhz[2][index] != 2147000000) {// right time and not no-data
                helibb.putDouble(start2000);
                helibb.putDouble((double) oldbhz[1][index]);
                helibb.putDouble((double) oldbhz[2][index]);
                ncached++;
              }
              if (dbg) {
                long t = (long) ((start2000 + CWBWaveServer.year2000sec) * 1000.);
                // DEBUG: 
                par.prt(Util.clear(tmpsb).append(ttag).append("LSet ").append(Util.asctime(t)).
                        append(" on=").append(oldbhz[1][index]).append(" ox=").append(oldbhz[2][index]));
              }
              start2000 += 1.;

            }
          }
          int offsec = (int) (lastTimeOut2000 - time + 1.);  // Off set in data of first new sample (should be 1 second later)
          int offsettest = span.getIndexOfTime((long) ((lastTimeOut2000 + 1. + CWBWaveServer.year2000sec) * 1000.));
          if (offsec < 0) {
            if (dbg) {
              par.prt(Util.clear(tmpsb).append(ttag).append(" Got data way after.  How did this happen - gap? offsec=").
                      append(offsec).append(" time=").append(Util.ascdatetime2(span.getMillisAt(0))).
                      append(" LastTimeOut=").append(Util.ascdatetime2((long) (lastTimeOut2000 + CWBWaveServer.year2000sec))));
            }
            if (offsec < -300) {
              if (dbg) {
                par.prt(Util.clear(tmpsb).append(ttag).append(" declare a gap last data at last=").
                        append(CWBWaveServer.y2kToString(lastTimeOut2000)).append(" got ").
                        append(CWBWaveServer.y2kToString(time)));
              }
              lastTimeOut2000 = time - 1;
              //last1sec=data[0];
              for (int i = 0; i < 10; i++) {
                old[i] = data[0]; // fake up the prior 10 seconds
              }
              offsec = 0;
            } else {
              Util.clear(tmpsb).append(requestID).append(" 0\n");
              Util.stringBuilderToBuf(tmpsb, buf);
              out.write(buf, 0, tmpsb.length());
              return 0;
            }    // nothing to do but wait, but nothing returned?
          }
          int n = 0;
          //df[0] = last1sec;
          int ingap = -1;         // flag whether we have no data values
          if (dbg) {
            par.prt(Util.clear(tmpsb).append(ttag).append("Start decimation at offset=").
                    append(offsec).append("-").
                    append(Math.min(span.getLastData() - 10, span.getNsamp() - 10)).append(" time=").
                    append(span.timeAt(offsec)).append(" lastTime=").
                    append(CWBWaveServer.y2kToString(lastTimeOut2000)).append(" spantime=").
                    append(CWBWaveServer.y2kToString(time)).append(" doAll=").
                    append(doAll).append(" ").append(span));
          }
          //for(int i=off; i<off+nsec*irate; i=i+(irate/10)) {          // always do a whole number of seconds
          int navg = 0;
          double sum = 0;
          if (span.getNsamp() > 1) {
            for (int i = offsec; i < span.getNsamp(); i++) {// for data point was nsamp() -10
              if (data[i] == 2147000000) {
                ingap = 0;    // if a no data value, start a gap or extend the gap
              }
              if (ingap == -1) {
                if (Math.abs(span.getMillisAt(i) - lastMillisAt - 1000) > 1 && lastMillisAt > 0) {
                  if (dbg) {
                    par.prt(Util.clear(tmpsb).append(ttag).append(" ***** out of seq=").
                            append(span.getMillisAt(i)).append(" lst=").append(lastMillisAt).
                            append(" dff=").append(span.getMillisAt(i) - lastMillisAt));
                  }
                }
                lastMillisAt = span.getMillisAt(i);
                df[n] = (int) filter.filter(data[i]);
                sum += df[n];
                navg++;
                n++;
                //par.prt(i+" "+n+" d="+data[i]+" df="+df[n-1]+" "+span.timeAt(i));
                //par.prt(i+" "+n+" d="+data[i]+" filt="+tmp+" df="+df[n-1]+" "+span.timeAt(i));
                /*if(lpmean == 0.) lpmean = df[n];
                lpmean = (lpmean * 9999. + df[n])/10000.;
                par.prt(i+" "+n+" d="+data[i]+" lpmean="+lpmean+" df="+df[n]+"  dm="+(int)(df[n] - lpmean));*/

                //df[n] = (int) (df[n] - lpmean);
                //n++;
              } else {  // its in a gap, count up good samples until we get to 10 then gap is over
                if (ingap == 0) {
                  if (doAll) {
                    filterAll.initialize();
                  } else {
                    filter.initialize();
                  }
                }
                ingap++;
                df[n] = (int) filter.filter(data[i]);     // build up 10 samples
                if (ingap >= 40) {
                  ingap = -1;  // gap is over and so is the IIR filter warmup
                }
                df[n++] = 2147000000;     // Mark no data in this part of the filter
              }
            }
          }

          if (dbg) {
            par.prta(ttag + " n=" + n + " " + df[0] + " " + df[1]);
          }
          if (n > 1) {
            int avg = (int) (sum / navg);
            for (int ii = 0; ii < n; ii++) {
              if (df[ii] != 2147000000) {
                df[ii] = (df[ii] - avg) / 10;
              }
            }
            int st = Math.min(span.getFirstMissing() - 10, span.getNsamp() - 9);
            if (n < 10) {
              par.prt(ttag + "   *********** N< 10 st=" + st);
            }
            time = time + offsec;
            for (int i = 0; i < n; i++) {
              diff = addHeliBB(df, Math.max(i - 1, 0), Math.min(i + 2, n), time, -.5, helibb, dbg);
              if (diff > max) {
                max = diff;
              }
              if (diff > 3000 && dbg) {
                par.prta(ttag + " ***Big value " + CWBWaveServer.y2kToString(time) + " diff=" + diff + " i=" + i + " 1st mssing=" + span.getFirstMissing());
              }
              time += 1.;
            }
          }
        } /* for > 1 Hz data we decimate whole seconds starting on second boundaries going forward */ else {
          int gapskip = 0;
          int off;
          if (df.length < data.length) {
            df = new int[span.getNsamp() * 4 / 3];
          }
          if (lastTimeOut2000 <= 0.) {   // First blocks, set up preevent stuff
            lastTimeOut2000 = time - 1.;     // This is the first whole second
          }
          if (doAll) {
            off = (int) ((span.getMillisAt(0) % 1000) / 1000. * irate);
          } //off = (int) (span.getMillisAt(0)/1000.*irate+irate+0.001);
          else {// We need to know the offset to the even second after the lastTimeOut2000 to start processing
            //int offnew = span.getIndexOfTime((long) ((lastTimeOut2000 + CWBWaveServer.year2000sec+1.)*1000.));
            off = (int) (((lastTimeOut2000 + CWBWaveServer.year2000sec) + 1. - span.getMillisAt(0) / 1000. + 0.0005) * irate);
            //if(offnew != off) par.prta(ttag+" offnew="+offnew+" off="+off);
            if (dbg) {
              par.prt(Util.clear(tmpsb).append(ttag).append("doAll=").append(doAll).append(" off=").
                      append(off).append(" offtime=").append(span.timeAt(off)).append(" time=").
                      append(time).append(" ").append(Util.asctime((long) ((time + CWBWaveServer.year2000sec) * 1000.))).
                      append(" lastTimeOut2000=").append(lastTimeOut2000).append(" ").
                      append(Util.asctime((long) ((lastTimeOut2000 + CWBWaveServer.year2000sec) * 1000.))));
            }
          }
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append(CWBWaveServer.y2kToString(lastTimeOut2000)).
                    append(" got first full sec").append(CWBWaveServer.y2kToString(time)).
                    append(" offset to next=").append(off).append(" doAll=").append(doAll));
          }
          int n = 0;
          // Calculate number of samples into this buffer to start calculating on next 1 second boundary

          if (off < 0) {
            if (dbg) {
              par.prt(Util.clear(tmpsb).append(ttag).append(" Got data way after.  How did this happen - gap? off=").
                      append(off).append(" time=").append(Util.ascdatetime2(span.getMillisAt(0))).
                      append(" LastTimeOut=").append(Util.ascdatetime2((long) (lastTimeOut2000 + CWBWaveServer.year2000sec))));
            }
            if (off / irate < -300) {
              if (dbg) {
                par.prt(Util.clear(tmpsb).append(ttag).append(" declare a gap last data at last=").
                        append(CWBWaveServer.y2kToString(lastTimeOut2000)).append(" got ").
                        append(CWBWaveServer.y2kToString(time)));
              }
              lastTimeOut2000 = time - 1;
              off = (int) (span.getMillisAt(0) % 1000L) / ((int) (1000 / span.getRate()));// first sample in a new second;
              gapskip = 13;      // let the filters warm up again for 3 seconds
              filter.initialize();  // init the filter, new startup
            } else {
              Util.clear(tmpsb).append(requestID).append(" 0\n");
              Util.stringBuilderToBuf(tmpsb, buf);
              out.write(buf, 0, tmpsb.length());
              //out.write((requestID+" 0\n").getBytes());
              return 0;
            }    // Nothing return?

          }

          // if the request is for data before the last out, try to satisfy it from the cache
          if (start2000 < lastTimeOut2000 && !doAll) {
            if (start2000 < lastTimeOut2000 - MAX_CACHE - 1) {
              start2000 = lastTimeOut2000 - MAX_CACHE - 1.;
            }
            while (start2000 <= lastTimeOut2000) {
              int index = ((int) (start2000)) % MAX_CACHE;
              if ((int) (start2000 + 0.001) == oldbhz[0][index] && oldbhz[2][index] != 2147000000) {
                helibb.putDouble(start2000 + .5);
                if (oldbhz[1][index] < oldmin) {
                  oldmin = oldbhz[1][index];
                }
                if (oldbhz[2][index] > oldmax) {
                  oldmax = oldbhz[2][index];
                }
                helibb.putDouble((double) oldbhz[1][index]);
                helibb.putDouble((double) oldbhz[2][index]);
                if (span.getSeedname().equals("USGOGA BHZ00")) {
                  long t = (long) ((oldbhz[0][index] + CWBWaveServer.year2000sec) * 1000. + 500.);
                  String s = "Set " + Util.asctime(t) + " on=" + oldbhz[1][index] + " ox=" + oldbhz[2][index];
                  // DEBUG: 
                  if (dbg) {
                    par.prt(ttag + s);
                  }
                }
                //if(dbg) 
                //  par.prt(start2000+" "+oldbhz[0][index]+" cache="+CWBWaveServer.y2kToString(start2000)+" "+oldbhz[1][index]+" "+oldbhz[2][index]);
              }
              start2000 += 1.;
              ncached++;
            }
          }
          time = lastTimeOut2000 + 1.;
          int last = span.getNsamp();  // Last good sample in data buffer
          int indexAtEnd = span.getIndexOfTime((long) ((iend + CWBWaveServer.year2000sec) * 1000. + 0.0001));
          nsec = (Math.min(last, indexAtEnd) - off) / irate - 1;                            // Number of full seconds on second boundary from off to end
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append("Start decimation at time=").
                    append(span.timeAt(off)).append(" off=").append(off).append(" nsec=").
                    append(nsec).append(" lastTime=").append(CWBWaveServer.y2kToString(lastTimeOut2000)));
          }
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append("start Filtering ").append(nsec * irate).
                    append(" samp ").append(Util.ascdatetime2(span.getMillisAt(off))).
                    append(" lastmillis=").append(Util.ascdatetime2(lastMillisAt)).
                    append(" doAll=").append(doAll).append(" ").append(span));
          }
          int ngap = 0;
          boolean inGap = false;
          for (int i = off; i < off + nsec * irate; i++) {          //??/10?? always do a whole number of seconds
            // if all samples for one second are good data, filter it, if one is no-data, make filtered data point no data
            if (data[i] == 2147000000) {
              df[n++] = 2147000000;   // Some nodata in buffer means filter is no data, this is how gaps go by
              ngap++;
              inGap = true;
            } else {
              if (inGap) {
                if (doAll) {
                  filterAll.initialize();
                } else {
                  filter.initialize();
                }
                inGap = false;
              }
              if (doAll) {
                df[n++] = (int) filterAll.filter(data[i]);
              } else {
                df[n++] = (int) filter.filter(data[i]);

                //check that we are processing data exactly as expected
                /*long it = span.getMillisAt(i);
                if(Math.abs(it - lastMillisAt -1000/irate) > 500/irate) {
                  if(lastMillisAt != 0) 
                    par.prt(ttag+" "+i+ " *** No continuous filter time="+Util.ascdatetime2(it)+
                            " is not right after last="+
                          " "+(it - lastMillisAt)+" "+Util.ascdatetime2(lastMillisAt)+" #gap="+ngap);
                  if(doAll) filterAll.initialize();
                  else filter.initialize();
                }
                else ngap=0;
                lastMillisAt = it;*/
              }
            }
          }
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append("done filtering n=").append(n));
          }
          diff = 0;
          // for all of the new filtered data, process it via addHeliBB
          for (int i = 0; i < n; i++) {
            //if(time+.5 > iend) break;
            if (df[i] == 2147000000) {
              gapskip = 3 * irate;//  do not send out the sample as its all Gibbs phenomenum
            }
            if (i % irate == 0) {
              if (gapskip <= 0) {
                diff = addHeliBB(df, i, Math.min(i + irate + 1, n), time, -.5, helibb, span.getSeedname().equals("USGOGA BHZ00"));
              }
              if (diff > max) {
                max = diff;
              }
              time += 1.;
            }
            gapskip--;       // count down the Gibbs avoidance.
            //if(dbg) par.prta("Time at="+span.timeAt(off+i*4));
          }
        }

        // Now send the return buffer
        int buflen = helibb.position();
        helibb.position(0);
        helibb.putInt(buflen / 24);
        if (dbg) {
          par.prta(Util.clear(tmpsb).append(ttag).append("HELI return ").append(span.getSeedname()).
                  append(" lasttime=").append(CWBWaveServer.y2kToString(lastTimeOut2000)).
                  append(" len=").append(buflen).append(" ns=").append(buflen / 24).
                  append(" maxdiff=").append(max).append(" maxoldiff=").
                  append(oldmax - oldmin).append(" #cached=").append(ncached));
        }
        if (buflen / 24 > 0) {
          boolean compress = true;
          if (word.length >= 6) {
            compress = word[6].charAt(0) != '0';
          }
          if (compress) {
            buflen = CWBWaveServer.writeBytesCompressed(requestID, helibyte, 0, buflen, out);
            bytesOut += buflen;
            return buflen;
          } else {
            Util.clear(tmpsb).append(requestID).append(" ").append(buflen).append("\n");
            Util.stringBuilderToBuf(tmpsb, buf);
            out.write(buf, 0, tmpsb.length());
            //out.write((requestID+" "+buflen+"\n").getBytes());
            out.write(helibyte, 0, buflen);
            bytesOut += buflen;
            return buflen;
          }
        } else {
          Util.clear(tmpsb).append(requestID).append(" 0\n");
          Util.stringBuilderToBuf(tmpsb, buf);
          out.write(buf, 0, tmpsb.length());
          //out.write((requestID+" 0\n").getBytes());
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(ttag).append(" no data returned3"));
          }
          return 0;
        }

        //return 0;
      }   // End of synchronized on mutex

    } catch (IOException e) {
      throw e;
    } catch (RuntimeException e) {
      par.prt(Util.clear(tmpsb).append(ttag).append(" Runtime Exception:  send no data returned4"));
      e.printStackTrace(par.getPrintStream());
      Util.clear(tmpsb).append(requestID).append(" 0\n");
      Util.stringBuilderToBuf(tmpsb, buf);
      out.write(buf, 0, tmpsb.length());
      //out.write((requestID+" 0\n").getBytes());
      throw e;
    }
  }

  /*private int writeBytes(StringBuilder requestID, StringBuilder [] word, byte [] helibuf, int off, int buflen, OutputStream  out) throws IOException {
    boolean compress=true;
    if(word.length >= 6 )
      compress = !word[6].equals("0");
   if(buflen/24 > 0) {
     if(compress) {
       buflen= CWBWaveServer.writeBytesCompressed(requestID, helibyte, 0, buflen, out);
       bytesOut+=buflen;
       return buflen;
     }
     else {              
       out.write((requestID+" "+buflen+"\n").getBytes());
       out.write(helibyte, 0, buflen);
       bytesOut+=buflen;
       return buflen;
     }
   } 
   else {
     out.write((requestID+" 0\n").getBytes());
     if(dbg)
       par.prta(ttag+" no data returned3");           
     return 0;
   }   
  }*/

  /**
   * Process one second of data (the offset and end are the point in one
   * second). Put one data value in output buffer and cache if all data are ok.
   * Else no output data and put no-data value in cache for this second
   *
   * @param d filter data buffer
   * @param off Staring offset to process
   * @param end ending offset to process
   * @param time Time of off data
   * @param delay Delay to add to time for output data (the group delay time
   * from filtering)
   * @param bb The bytebuff to write with Heli filter data in WWS format
   * @return The offset between min and max
   */
  private int addHeliBB(int[] d, int off, int end, double time, double delay, ByteBuffer bb, boolean dbg) {
    int mn = Integer.MAX_VALUE;
    int mx = Integer.MIN_VALUE;
    for (int i = off; i < end; i++) {
      if (d[i] < mn) {
        mn = d[i];
      }
      if (d[i] > mx) {
        mx = d[i];    // if nodata is found in d, then mx will be the no data value
      }
    }
    if (mn == Integer.MAX_VALUE) {
      return 0;
    }
    if (mx - mn > 30000 && mx != 2147000000) {
      if (dbg) {
        par.prt(ttag + " *** Large value mn=" + mn + " mx=" + mx);
      }/*return mx-mn;*/
    }
    // if this group had all good data, put it in WWS output buffer
    if (mx != 2147000000) {
      if (time > lastTimeOut2000) {  // if this is a new second, put it in the cache
        lastTimeOut2000 = time;
        int index = ((int) (time - delay + 0.001)) % MAX_CACHE;
        oldbhz[0][index] = (int) (time + 0.000001);
        if (mn == mx) {
          mn--;
          /*mx++;*/
        }
        oldbhz[1][index] = mn;
        oldbhz[2][index] = mx;
      }
      bb.putDouble(time - delay);
      bb.putDouble((double) mn);
      bb.putDouble((double) mx);
      long t = (long) ((time + CWBWaveServer.year2000sec - delay) * 1000.);
      if (dbg) {
        String s = "Set " + Util.asctime(t) + " mn=" + mn + " mx=" + mx;
        // DEBUG: 
        //par.prt(ttag+s);
      }
      return mx - mn;
    }
    return -1;    // This indicates no data was saved, i.e. some nodata value was found
  }
}
