/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.msread;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;
/** This program converts data in 1024 byte ISA 10.4 format to MiniSEED putting each channel 
 * in its own MiniSEED file (NNSSSS_CCCLL_yyyy_doy_HHMM.ms).  The conversion code was lifted from the ISILink task in EdgeMom.
 * All output goes to a log file ala EdgeThread since this needed to be an EdgeThread to play
 * nice with RawToMiniSEED.  It was originally written to convert data from the FSU stations that 
 * got ISA 10.4 files put in an FTP area which contained data not telemetered.
 * 
 * This class implements a switch option in msread that does is used by the DCC staff to do the conversion.
 *
 * Usage: create a new ISALoader() and then call doFile(String filename) for each file.  The
 * name of the station is not in the data, so this name is passed to the creator as the 2nd argument (tag).
 * 
 * @author d.c. ketchum U.S. Geological Survey <ketchum at usgs.gov>
 */
public class ISALoader extends EdgeThread implements MiniSeedOutputHandler {
  private static final DecimalFormat df2 = new DecimalFormat("00");
  private static final DecimalFormat df3 = new DecimalFormat("000");
  private static final DecimalFormat df4 = new DecimalFormat("0000");
  private static final DecimalFormat df6 = new DecimalFormat("000000");
  private final TreeMap<String, RawDisk> outfiles = new TreeMap<>();
  private static boolean dbg;
  @Override
  public StringBuilder getMonitorString() {return monitorsb;}
  @Override
  public StringBuilder getStatusString() {return statussb;}
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
  @Override
  public void terminate() {}
  public static void setDebug(boolean t) {dbg=t;}
  @Override
  public void close() {
    RawToMiniSeed.forceStale(-1);   // Force out an partial blocks
    try {
      Iterator<RawDisk> itr = outfiles.values().iterator();
      while(itr.hasNext()) {
        RawDisk put = itr.next();
        if(put != null) put.close();
      }
    } 
    catch(IOException e) {}
  }
  /** RawToMiniSeed calls this handler with each completed miniseed block.
   * This routine creates the by channel filenames and puts the data in the right file.
   * @param buf The miniseed buffers from RTMS
   * @param len Normally 512 for the length of buf!
   */
  @Override
  public void putbuf(byte [] buf, int len) {
    try {
      String seedname = MiniSeed.crackSeedname(buf).replaceAll(" ","_");
       RawDisk put = outfiles.get(seedname);
      if(put == null) {
        MiniSeed ms = new MiniSeed(buf,0,len);
        GregorianCalendar g = ms.getGregorianCalendar();
        put = new RawDisk(seedname+"_"+df4.format(g.get(Calendar.YEAR))+"_"+df3.format(g.get(Calendar.DAY_OF_YEAR))+
                "_"+df2.format(g.get(Calendar.HOUR_OF_DAY))+df2.format(g.get(Calendar.MINUTE))+".ms", "rw");
        put.position(0);
        put.setLength(0L);
        outfiles.put(seedname, put);
       
      }
      put.write(buf, 0, len);
      MiniSeed ms = new MiniSeed(buf, 0, len);
      if(dbg)
        prt(ms.toString());
    }
    catch(IOException e) {
      prta("IOError e="+e);
      e.printStackTrace(getPrintStream());
    }
    catch(IllegalSeednameException e) {
      prta("Illegal seedname="+e);
    }
    
  }
  /** You have to create one of these to convert data from one station.  The stat argument should be in NNSSSS form
   * 
   * @param argline A argline for EdgeThread for controlling output
   * @param stat  The network and station to hang on this file (10.4 does not contain this information)
   */
  public ISALoader(String argline, String stat) {
    super(argline, stat);
    station=stat;
  }
  /** Call this routine with each file to be converted.  The file naming and output are in putbuf().
   * 
   * @param filename The inpub ISA 10.4 formatted file.
   */
  public void doFile(String filename) {
    RawToMiniSeed.setStaticOutputHandler(this);
    byte [] b = new byte[10240];
    try {
      RawDisk rw = new RawDisk(filename,"rw");
      if(dbg)
        prta("Starting file="+filename+" length="+rw.length());
      for(int iblk=0; iblk<rw.length()/512; iblk=iblk+2) {
        rw.readBlock(b, iblk, 1024);
        isiIDA10(b, 0, 0L, 1024);
      }
    }
    catch(EOFException e) {
      if(dbg) 
        prta("EOF reached on file="+filename);
    }
    catch(IOException e) {
      
      Util.prt("e="+e);
      e.printStackTrace();
    }
  }
    /** this callback routine handles IDA 10.4 data
   * @param buf The byte buffer with the packet
   * @param sig The signature int (series) on this packet
   * @param seq The sequence of his packe
   * @param len The length of this packet (normally 1024)
   *
   */
  public final void isiIDA10(byte [] buf, int sig, long seq, int len) {
    ByteBuffer bb  = ByteBuffer.wrap(buf);
    //doSequenceChecking(sig,seq);
    bb.position(0);
    byte t = bb.get();        // common header
    byte s = bb.get();
    if(t != 'T' || s != 'S') {prta(tag+" Got IDA10 packet but its not TS - no implemented "+((char) t)+((char) s));return;}
    byte version = bb.get();
    byte subvers = bb.get();
    if(subvers == 4) isiIDA10_4(bb, sig, seq, len);
    else {
      prta(tag+" Got IDA10 packet of unimplemented version="+version+"."+subvers);
    }
  }
  private final GregorianCalendar gnow = new GregorianCalendar();
  private TraceBuf tb ;
  private int countMsgs;

  private String station="IUMA2";
  private void isiIDA10_4(ByteBuffer bb, int sig, long seq, int len) {
    countMsgs++;
    // Decode the Header
    long unitserial = bb.getLong();
    int seqnumber330 = bb.getInt();        //!330 time 24 bytes
    int secoffset = bb.getInt();
    int usecoffset = bb.getInt();
    int nanoIndexOffset = bb.getInt();    // offset in NANOs to the first sample in IDA packet
    int filterMicros = bb.getInt();
    short lockTime  = bb.getShort();
    byte clockBitMap = bb.get();
    byte clockQual = bb.get();
    // Rest of common header
    int idaseq = bb.getInt();
    int hostTime = bb.getInt();
    short reserved = bb.getShort();
    short lcq = bb.getShort();
    short nbytes = bb.getShort();

    // This is the data area
    byte [] streamChar = new byte[6];
    bb.get(streamChar);
    for(int i=0; i<6; i++) if(streamChar[i] < 32) streamChar[i] = 32;
    String stream = new String(streamChar).toUpperCase();
    byte format = bb.get();
    byte conversionGain = bb.get();
    short nsamp = bb.getShort();
    short rateFactor = bb.getShort();
    short rateMultiplier = bb.getShort();

    int julian = (secoffset+seqnumber330)/86400+SeedUtil.toJulian(2000,1);
    int [] ymd = SeedUtil.fromJulian(julian);
    int doy = SeedUtil.doy_from_ymd(ymd);
    int secs = (secoffset + seqnumber330) % 86400;
    int sec = secs;       // save time for RTMS in seconds.
    int usecs = usecoffset+nanoIndexOffset/1000-filterMicros;
    // If the usecs is negative, make it positive by borrowing seconds
    while(usecs < 0) {
      sec--;
      usecs += 1000000;
    }
    // if the sec is now zero, then need to go to previous day
    if(sec < 0) {
      julian--;
      ymd = SeedUtil.fromJulian(julian);
      doy = SeedUtil.doy_from_ymd(ymd);
      sec += 86400;
    }

    // Convert to displays variables for human readable time
    int hr = secs /3600;
    secs = secs % 3600;
    int min = secs/ 60;
    secs = secs % 60;
    int ioClock=0;
    if(clockQual >= 80) ioClock |= 32;
    int quality=0;
    int activity=0;
    double rate=rateFactor;
    // if rate > 0 its in hz, < 0 its period.
    // if multiplier > 0 it multiplies, if < 0 it divides.
    if(rateFactor == 0 || rateMultiplier == 0) rate=0;
    if(rate >= 0) {
      if(rateMultiplier > 0) rate *= rateMultiplier;
      else rate /= -rateMultiplier;
    }
    else {
      if(rateMultiplier > 0)  rate = -rateMultiplier/rate;
      else rate = 1./rateMultiplier/rate;     // Note: both are negative cancelling out.
    }
    char first = stream.charAt(0);
    if(first != 'L' && first != 'B' && first != 'V' && 
            !stream.substring(0,Math.min(stream.length(),3)).equals("ACQ") && 
            !stream.substring(0,Math.min(stream.length(),3)).equals("ACO") && 
            !stream.substring(0,Math.min(stream.length(),3)).equals("AFP") && 
            !stream.substring(0,Math.min(stream.length(),3)).equals("ACP") ) {
      prt(tag+"IDA10.4 reject "+stream+
            " "+ymd[0]+"/"+df2.format(ymd[1])+"/"+df2.format(ymd[2])+" "+df2.format(hr)+":"+df2.format(min)+":"+
            df2.format(secs)+"."+df6.format(usecs).substring(0,4)+
            " tQ="+clockQual+" sq="+seq+" nb="+nbytes+
            " ns="+nsamp+" rt="+" rt="+rate);
      return;
    }
    else if(dbg)
      prt(tag+"IDA10.4 "+
            //" u="+Util.toHex(unitserial)+" "+
            stream+" "+ymd[0]+"/"+df2.format(ymd[1])+"/"+df2.format(ymd[2])+" "+df2.format(hr)+":"+df2.format(min)+":"+
            df2.format(secs)+"."+df6.format(usecs).substring(0,4)+
            //" sq330="+seqnumber330+" sec="+secoffset+" usec="+usecoffset+" nano="+nanoIndexOffset+" filt="+filterMicros+
            //" lock="+lockTime+" cbit="+Util.toHex(clockBitMap)+
            " tQ="+clockQual+/*" iseq="+idaseq+*/" sq="+seq+" nb="+nbytes+
            //" frm="+format+" cv="+conversionGain+
            " ns="+nsamp+" rt="+/*rateFactor+"/"+rateMultiplier+*/" rt="+rate);

    if(format == 0) {
      int [] data = new int[nsamp];
      for(int i=0; i<nsamp; i++) data[i] = bb.getInt();
      String seedname = (station+"      ").substring(0,7)+stream.substring(0,5);
      StringBuilder seednameSB = new StringBuilder(12);
      seednameSB.append(seedname);
      RawToMiniSeed.addTimeseries(data, (int) nsamp, seednameSB, ymd[0], doy, sec, usecs,
            rate, activity, ioClock, quality, (int) clockQual, this);

 
        //prta(seedname+" "+Util.asctime2(gnow)+" rt="+nsamp+tb);
    }
    else {
      prt(tag+" ****** IDA10.4 format not implemented="+format);
    }
  }

  public static void main(String [] args) {
    Util.init("edge.prop");
   ISALoader tmp = new ISALoader("-empty >>isiloader", args[0]);
   for(int i=1; i<args.length; i++) {
     tmp.doFile(args[i]);
   }
   tmp.close();
  }
}
