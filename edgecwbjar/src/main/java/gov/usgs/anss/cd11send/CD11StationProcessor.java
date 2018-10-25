/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */



package gov.usgs.anss.cd11send;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.cd11.CD11Frame;
import gov.usgs.anss.cd11.GapList;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgemom.CD11SenderClient;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.BufferOverflowException;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * CD11StationProcessor.java - This controls the queue for one station for output via
 * CD1.1.  It processes miniseed data via the queue() method, and as 10 second blocks of data
 * are available for a station, it make CD1.1 blocks and writes them into another ring (station.cd11out).
 * This routine starts the CD11SenderClient which reads data from the .cd11out ring and sends
 * it via TCP/IP to the ultimate destination.  The CD11SenderClient creates a CD11SenderReader which
 * reads from the connection and processes acks.
 * 
 * This processes thread run() is used to look at the sequence gaps maintained in the CD11SenderClient and
 * processes CWBQuery requests to fill this data and put it on the link.  It queues this
 * data for output on the socket link through the CD11SenderClient.  It also sleeps to insure any
 * output OOB data is delivered no faster than 10 times real time.
 * 
 * 
 * <br>
 * <PRE>
 * -secdepth sec The length of the in-the-clear buffers in seconds for this station 
 * -recsize  size The length of the records in bytes in the cd1.1 ring (must be larger than any records written!)
 * -maxrec   max  The number of records in the CD1.1 ring (the modulo of the ring)
 * -creator  creator The creator string to put on the CD1.1 link 
 * -destination dest  The destination string to use on the CD1.1 link
 * -lmap FR=TO[/F2=T2..]  Change the location code from FR->TO and F2->T2 etc.  Spaces should be input as "-"
 *                    If this switch is not used, all location codes will be removed.  To preserve location codes
 *                    use -lmap NN=NN.
 * -cmap FRM=TO1[/fR2=TO2] Change channel names from FRM->TO11 etc.  So BHZ=XYZ/BH1=XY1 
 * 
 * Parameters used by the CD11SenderClient only :
 * -p        port     The port the CD11ClientSender is to send the data to (the well known port)
 * -ip       ip.adr   The IP address to send the data to (CD11SenderClient)
 * -b        ip.adr   The IP address on the local computer to which the socket is to be bound(CD11SenderClient)
 * 
 * </PRE>
 *
 * Created on March 29, 2007, 5:22 PM
 *
 *@author davidketchum
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
public class CD11StationProcessor extends Thread {
  private CD11SenderClient sender;
  private final TreeMap<String, ZeroFilledSpan>spans = new TreeMap<>();// Contains unsent spans of data from real time data
  private int countPackets;
  private long lastSent;
  private double secsDepth;
  private String tag;
  private long latencyAvg;
  private long nlatency;
  private long minLatency;
  private long maxLatency;
  private GregorianCalendar nextTime;          // Time of next desired buffer
  private final GregorianCalendar outTime=new GregorianCalendar();            // used in time calculations
  private int maxLengthOut;             // keep track of biggest output bytes to size file recsize
  private int nextin;
  private final String station;       // 7 characer NNSSSSS seedname
  private ServerSocket ss;      // The listener socket
  private CD11StationProcessorShutdown shutdown;
  private CD11SenderClientMonitor senderMonitor;
  private EdgeThread par;
  private boolean terminate;
  private boolean dbg;
  private final int [] data = new int[1000];
  // Related to output frame
  private CD11Frame cd11frame;
  private byte [] framebuf;
  private ByteBuffer bbframe;
  private final StringBuilder creator = new StringBuilder(10);
  private final StringBuilder destination = new StringBuilder(10);
  private final String configFile;
  
  // CD11 ring parameters
  RawDisk cd11ring;             // Connection to ring file with cd11 for output
  private int maxrec;           // Maximum number of records in the CD11 format file
  private int recsize;          // The size in blocks of records in the CD11 format file
  private int recsizebytes;     // The record size in bytes
  private long lastSeqOut;      // The last sequence number written into ring (the newest not the last write)
  private final byte [] cd11hdrbuf = new byte[20];
  private ByteBuffer cd11hdr;
  
  // converting location/channel codes (mapping new location codes)
  private String locFrom[];
  private String locTo[];
  private String chnFrom[];
  private String chnTo[];
  
  // Check for config changes
  private String argline;
  private String arglineraw;
  private int state;
  private boolean nogap;
  private String ringPath ="";
  private final StringBuilder tmpsb = new StringBuilder(50);
  public boolean isConnected() { if(sender == null) return false; return sender.isConnected();}
  public String getStation() {return station;}
  /** terminate the given CD11SenderClient object if it is on the list  */
  public void terminate(){
    terminate=true;
    interrupt();
    par.prt(Util.clear(tmpsb).append(tag).append(" terminate called"));
  }

  /** return a string with status on each CD11SP and connections to same
   *@return The string. */
  public String getStatus() {
    return toString()+"\n"+sender.getStatusString();
  }

  /** return a string with latency statistics and reset the statistics
   *@return The string */
  public String getStatistics() {
    String s= station+" Latency min="+minLatency+" max="+maxLatency+" #pck="+nlatency+
            " avg="+(nlatency > 0?(latencyAvg/nlatency):0);
    nlatency=0;
    latencyAvg=0;
    minLatency=100000000;
    maxLatency=-100000000;
    return s;
  }
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append("CD11StaProc: ").append(station).append(" queue : nxtin=").append(nextin).
              append(" maxLen=").append(maxLengthOut).append("/").append(recsize).append(" st=").append(state).append(" alive=").append(isAlive());
      
    }
    return sb;
  }
  /** Queue a MiniSeed block and process it to in-the-clear buffers.  It also does
   * all the processing to send data out, skip ahead when needed used to create the CD11 packet.
   * 
   * @param ms The MiniSeed block to add
   */
  public synchronized void queue(MiniSeed ms) {

    countPackets++;
    // If this data is much newer, we need to run time forward until we get to this time
    if(ms.getTimeInMillis() - nextTime.getTimeInMillis() > 180000) {  // move forward through and send what we can 
      while(nextTime.getTimeInMillis() < ms.getTimeInMillis() - 30000) {
        try {
          if(windowOK(nextTime, spans)) {
            if(dbg) 
              par.prta(Util.clear(tmpsb).append(tag).append(" Send skip ahead ").append(Util.asctime2(nextTime)));
            buildFrame(cd11frame, spans, nextTime);
            try {
              writeFrame();
            }
            catch(IOException e) {
              par.prta(Util.clear(tmpsb).append(tag).append(" IOError writing to CD11ring file2 e=").append(e));
              e.printStackTrace(par.getPrintStream());

            }
          }
          else {  // no new frame, so manually advance to next possible time
            if(dbg) 
              par.prta(Util.clear(tmpsb).append(tag).append(" Send No skip ahead ").append(Util.asctime2(nextTime)));
            nextTime.setTimeInMillis(nextTime.getTimeInMillis()+10000);
            Iterator<ZeroFilledSpan> itr = spans.values().iterator();
            while(itr.hasNext()) {
              ZeroFilledSpan span = itr.next();
              span.removeFirst((nextTime.getTimeInMillis() - span.getStart().getTimeInMillis())/1000.);        // Shift data down by 10 seconds
            }
          }
        }
        catch(IndexOutOfBoundsException e) {
          if(dbg) 
            par.prta(Util.clear(tmpsb).append(tag).append(" ").append(e));
          nextTime.setTimeInMillis(nextTime.getTimeInMillis()+10000);
          if(e.toString().indexOf("out of buffer") > 0) {   // There is no more data in the buffer, zap it to add the nex
            Iterator<ZeroFilledSpan> itr = spans.values().iterator();
            while(itr.hasNext()) itr.next().clear();        // Make all channels empty
            break;
          }
        }
      }
    }
    try {
      ZeroFilledSpan chan = spans.get(ms.getSeedNameString());
      if(chan == null) {
        chan = new ZeroFilledSpan(ms, ms.getGregorianCalendar(), 300., 2147000000);
        spans.put(ms.getSeedNameString(), chan);
      }
      else chan.addMiniSeed(ms);      // Already have it, just add it.
    }
    catch (ArrayIndexOutOfBoundsException e) {
      e.printStackTrace(par.getPrintStream());
    }
    // See if its time to try to process some data
    if( countPackets > 20) {   // allow 20 packets of warm up from a station (to insure one of each channel is in)
      try {
      
        while(windowOK(nextTime, spans)) {
          // if we have the data, build up the frame
          if(dbg) 
            par.prta(Util.clear(tmpsb).append(tag).append(" Send next realtime ").append(Util.asctime2(nextTime)));
          buildFrame(cd11frame, spans, nextTime);
          writeFrame();
          long lat  = System.currentTimeMillis()%86400000L - nextTime.getTimeInMillis()+10000;
          //if(MiniSeed.crackSeedname(buf).substring(7,10).equals("LOG")) par.prta("LOG recout2 "+MiniSeed.crackSeedname(buf));
          if(ms.getSeedNameString().substring(7,10).equals("BHZ")) {
            if(lat < minLatency) minLatency=lat;
            if(lat > maxLatency) maxLatency=lat;
            latencyAvg += lat;
            nlatency++;
          }
        }
      }
      catch(IndexOutOfBoundsException | BufferOverflowException e) {
        if(dbg) 
          par.prta(Util.clear(tmpsb).append(tag).append(" skip forward ").append(e.getMessage()));
        if(e.toString().indexOf("out of buffer") > 0) {   // There is no more data in the buffer, zap it to add the nex
          Iterator<ZeroFilledSpan> itr = spans.values().iterator();
          while(itr.hasNext()) itr.next().clear();        // Make all channels empty
        }        
        else 
          nextTime.setTimeInMillis(nextTime.getTimeInMillis()+10000); // its never coming in!
      }
      catch(IOException e) {
        par.prta(Util.clear(tmpsb).append(tag).append(" IOError writing to CD11ring file e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }
    }
  }
  private final StringBuilder nameString = new StringBuilder(15);
  private synchronized void buildFrame(CD11Frame cd11frame, TreeMap<String, ZeroFilledSpan>sp, GregorianCalendar next) {

    Iterator<ZeroFilledSpan> itr = sp.values().iterator();
    cd11frame.loadDataFrame(next, 10000 );
    while(itr.hasNext()) {
      ZeroFilledSpan span = itr.next();
      int nsamp = (int) (10.*span.getRate()+.0001);
      span.getData(next, nsamp, data,outTime);
      Util.clear(nameString).append(span.getSeedname().substring(2,10).trim());
      //String nameString=span.getSeedname().substring(2,10).trim();
      
      // do any channel substitutions
      if(chnFrom != null ) {
        String chan = span.getSeedname().substring(7);
        for(int i=0; i<chnFrom.length; i++) {
          if(chan.equals(chnFrom[i])) {
            chan = chnTo[i];
            //par.prt("Change chan "+span.getSeedname().substring(7)+" to "+chan);
            if(nameString.length() >= 5) nameString.delete(5, nameString.length());
            nameString.append(chan);
            //nameString = nameString.substring(0,5)+chan;
          }
        }
      }
      // do any location code substitutions
      if(locFrom != null) {
        String loc = (span.getSeedname()+"      ").substring(10,12);
        for(int i=0; i<locFrom.length; i++) {
          loc = loc.replaceAll(locFrom[i], locTo[i]);
        }
        nameString.append(loc.trim());
        //nameString = nameString+loc.trim();
        //if(dbg) 
        //  par.prt("Would change name from "+span.getSeedname()+" to "+nameString+"|");
      }
      //par.prt("Add channel "+nameString+" "+Util.asctime2(outTime)+" rt="+span.getRate()+" ns="+nsamp+" span="+span);
      StringBuilder ans= cd11frame.addChannel(nameString, outTime, nsamp, span.getRate(), data, false, 1, 0); // Candadian before auth, seismic data
      //if(dbg) par.prt(ans.toString());
      span.removeFirst((next.getTimeInMillis()+10000 - span.getStart().getTimeInMillis())/1000.);        // Shift data down by 10 seconds
    }
    next.setTimeInMillis(next.getTimeInMillis()+10000);
    
  }
  private boolean windowOK(GregorianCalendar g, TreeMap<String, ZeroFilledSpan> sp) {
    if(countPackets < 10) return false;
    GregorianCalendar nominal = null;
    Iterator<ZeroFilledSpan> itr = sp.values().iterator();
    boolean ok=true;
    while(itr.hasNext()) {
      ZeroFilledSpan span = itr.next();
      if(nominal == null) nominal = span.getStart();
      try {
        if(span.hasFill(g, 10.)) {ok=false; break;}
      }
      catch(IndexOutOfBoundsException e) {
        e.printStackTrace(par.getPrintStream());
        throw e;
      }
    }    
    return ok;
  }
  /** Creates a new instance of CD11SP
   * @param config The configuration file name with per station configs
   * @param ms A miniseed  packet to get things started.
   * @param parent The parent EdgeThread to use for logging
   * @throws FileNotFoundException if the config file cannot be opened
   * @throws UnknownHostException if the CD11SenderClient cannot find the host or bind its address
   */
  public CD11StationProcessor(String config,  MiniSeed ms, EdgeThread parent) throws FileNotFoundException, UnknownHostException {
    station = ms.getSeedNameString().substring(0,7).trim();
    par = parent;
    configFile = config;
    arglineraw = getConfigLine(station);
    argline=arglineraw;
    if(argline == null) throw new FileNotFoundException(station+" is not found in configfile="+configFile);
    cd11hdr = ByteBuffer.wrap(cd11hdrbuf);
    tag = "CD11SP["+station+"]";
    parseArgs(arglineraw);
    nextTime = new GregorianCalendar();
    nextTime.setTimeInMillis(ms.getTimeInMillis() - (ms.getTimeInMillis() % 10000) + 10000);  // next 10 second boundary

    // Build the CD11Frame and the backing buffer and ByteBuffer
    if(argline.indexOf(">") > 0 ) argline=argline.trim()+".csend";
    else argline= argline.trim()+" >> "+station+".csend";
    par.prta(Util.clear(tmpsb).append(tag).append("Start CD11SenderClient with ").append(argline));
    par.prta("new ThreadProcess "+getName()+" "+getClass().getSimpleName()+"@"+Util.toHex(hashCode())+
            " sender@"+Util.toHex(sender.hashCode())+" sendmon@"+Util.toHex(senderMonitor.hashCode())+" frm@"+Util.toHex(cd11frame.hashCode())+
            " t="+tag+" stat="+station+" l="+arglineraw);
            
    if(!nogap) start();
  }
  private void parseArgs(String argline) throws UnknownHostException {
    String [] args = argline.split("\\s");
    par.prt(Util.clear(tmpsb).append("CD11StationProcessor: Parse args: line=").append(argline));
    secsDepth = 300.;
    recsizebytes = 2048;
    maxrec = 8600;
    Util.clear(creator).append("usgs");
    Util.clear(destination).append("0");
    ringPath="";
    nogap=false;
    for(int i=0; i<args.length; i++) {
      //prt(i+" arg="+args[i]);
      if(args[i].length() == 0) continue;
      if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-empty")) {}         // Allow this for totally empty command lines
      else if(args[i].equals("-secdepth")) {secsDepth = Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-recsize")) {recsizebytes=Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-maxrec")) {maxrec = Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-creator")) {Util.clear(creator).append(args[i+1]); i++;}
      else if(args[i].equals("-ringpath")) {ringPath = args[i+1];if(!ringPath.endsWith("/")) ringPath+="/"; i++;}
      else if(args[i].equals("-destination")) {Util.clear(destination).append(args[i+1]); i++;}
      else if(args[i].equals("-nogap")) nogap=true;
      // Allow for parameters handled by the CD11SenderClient
      else if(args[i].equals("-p")) i++;
      else if(args[i].equals("-ip")) i++;
      else if(args[i].equals("-b")) i++;
      else if(args[i].equals("-lmap")) {
        String [] parts = args[i+1].split("/");
        if(parts.length > 0) {
          locFrom = new String[parts.length];
          locTo = new String[parts.length];
          for(int j=0; j<parts.length; j++) {
            String [] maps = parts[j].split("=");
            if(maps.length != 2) {
              par.prt("CD11StationProcessor -lmap is not in FR-TO/FR-TO/FR-TO form");
            }
            locFrom[j] = maps[0].replaceAll("-"," ");
            locTo[j] = maps[1].replaceAll("-"," ");
            par.prt(Util.clear(tmpsb).append(station).append(" Location convert ").append(locFrom[j]).append("->").append(locTo[j]).append("|"));
          }
        }
      }
      else if(args[i].equals("-cmap")) {
        String [] parts = args[i+1].split("/");
        if(parts.length > 0) {
          chnFrom = new String[parts.length];
          chnTo = new String[parts.length];
          for(int j=0; j<parts.length; j++) {
            String [] maps = parts[j].split("=");
            if(maps.length != 2) {
              par.prt("CD11StationProcessor -cmap is not in CCCLL-TOCLL:CCCL2=toCLL form");
            }
            chnFrom[j] = maps[0].replaceAll("-"," ");
            chnTo[j] = maps[1].replaceAll("-"," ");
            par.prt(station+" Channel convert "+chnFrom[j]+"->"+chnTo[j]+"|");
          }
        }
      }
      else if(args[i].substring(0,1).equals(">")) break;
      else par.prt(Util.clear(tmpsb).append(station).append(" CD11StationProcessor unknown switch=").
              append(args[i]).append(" ln=").append(argline));
    }
    recsize = (recsizebytes+511)/512;
    par.prta(Util.clear(tmpsb).append("CD11SP: created for ").append(station).append(" secepth=").append(secsDepth).
            append(" recsize=").append(recsize).append(" maxrec=").append(maxrec).
            append(" creator=").append(creator).append(" dest=").append(destination));
    framebuf = new byte[recsize*512];
    bbframe = ByteBuffer.wrap(framebuf);      // note: memory leak possible here, do not know how to release bbframe
    try {
      if(cd11ring != null) {
        par.prt(Util.clear(tmpsb).append("Close ring file connectin to ").append(cd11ring.getFilename())); 
        cd11ring.close();
      }
      cd11ring = new RawDisk(ringPath+station+".cd11out", "rw");
      // is the file new?
      if(cd11ring.length() == 0) {
        par.prta(Util.clear(tmpsb).append(tag).append("CD11Ring file=").append(station).
                append(".cd11out is new create size=").append((maxrec*recsize+1)*512L));
        cd11ring.setLength((maxrec*recsize+1)*512L);      // Set the file length
        writeCD11Header();
      }
      else {
        if(readCD11Header()) {      // did the file rec size or length change?
          lastSeqOut = 0;           // no last sequence since the file is now different
          writeCD11Header();        // fix the header
          cd11ring.setLength((maxrec*recsize+1)*512L);  // reset the file size
        }
      }
    }
    catch(IOException e) {
      Util.IOErrorPrint(e,tag+"Trying to open the "+station+".cd11out file",par.getPrintStream());
    }
    if(sender != null) {par.prt(Util.clear(tmpsb).append(tag).append("Closing sender for reconfig"));sender.terminate();}
    sender = new CD11SenderClient(argline, station, spans);
    if(senderMonitor == null) senderMonitor = new CD11SenderClientMonitor();
    cd11frame = new CD11Frame(recsize*512, 0, creator, destination, 0L, 0, 0, sender);  // new cd11fram with creator/destination
    par = sender;       // Use the sender EdgeThread for logging from now on
  }
  /** read in the header
   * @return true if the file characteristics have changed from our configuration
   */
  private boolean readCD11Header() throws IOException {
    cd11ring.readBlock(cd11hdrbuf, 0, 20);
    cd11hdr.position(0);
    lastSeqOut = cd11hdr.getLong();
    int maxr = cd11hdr.getInt();
    int size = cd11hdr.getInt();
    if(maxr != maxrec || size != recsize) {
      par.prta(Util.clear(tmpsb).append(tag).append("CD11Ring file has changed characteristics rec now=").append(recsize).
              append(" was ").append(size).append(" maxrec=").append(maxrec).append(" was ").append(maxr));
      return true;    // The file characteristics have changed
    }
    return false;
    
  }
  public void writeCD11Header() throws IOException {
    cd11hdr.position(0);
    cd11hdr.putLong(lastSeqOut);
    cd11hdr.putInt(maxrec);
    cd11hdr.putInt(recsize);
    cd11ring.writeBlock(0, cd11hdrbuf, 0, 20);
  }
  /** Write out the frame currently in cd11frame
   * 
   * @throws java.io.IOException
   */
  byte [] zero = new byte[1];
  String zerobyte = new String(zero);
  private void writeFrame() throws IOException {
    int l = cd11frame.getOutputBytes(bbframe);    // Get the data into bbframe arounde framebuf
    /*par.prta("Write frame "+cd11frame.toOutputString()+" "+Util.toAllPrintable(cd11frame.getChannelString().toString()));
    CD11Frame tmp = new CD11Frame(10240,100);
    tmp.setDebug(true);
    ByteArrayInputStream in = new ByteArrayInputStream(framebuf);
    tmp.readFrame(in, par);
    for(int i=0; i<tmp.getDataNchan(); i++) {
      ChannelSubframe csf = tmp.getChannel(i);
      par.prt(i+" chan is "+csf.toString());
    }*/
    
    long seq = cd11frame.getOutSeq();
    if(seq < lastSeqOut -maxrec + 10) {         // is this out of the ring buffer?
      par.prta(Util.clear(tmpsb).append(tag).append(cd11frame).append(" is too old to put in CD11ring - skip it"));
      SendEvent.debugSMEEvent("CD11TooOld", cd11frame+" is too old to put in cd11ring "+station, this);
    }
    else {
      int iblk = (int) ((seq % maxrec)*recsize+1);  // COmpute the block to put the data
      cd11ring.writeBlock(iblk, framebuf, 0, l);
      if(l > maxLengthOut) maxLengthOut = l;
      if(dbg)
        par.prta(Util.clear(tmpsb).append(tag).append("Wr cd11 out to ring len=").append(l).append(" iblk=").append(iblk).append(" ").
                append(" sender.st=").append(sender.inState()).append(" ").
                append(cd11frame.toStringBuilder(null)).append(" ").
              append(cd11frame.getChannelString().toString().replaceAll(zerobyte, "-")));
      if(seq > lastSeqOut) {
        lastSeqOut = seq;        // keep lastSeqOut set to last frame output
        writeCD11Header();
      }
    }    
  }
  /** read the config file and return the line configuring this station
   * The configuration lines look like :
   * 
   * STAT:-p port [-b bind.addr]
   * @param station The station tag to find
   */
  private String getConfigLine(String station) throws FileNotFoundException {
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
        String line;
        int p=0;
        while ( (line = in.readLine()) != null) {
          if(line.charAt(0) == '#') continue;
          String [] tags = line.split(":");
          if(tags[0].equalsIgnoreCase(station)) {
            in.close();
            return tags[1];
          }
        }
      }
    }
    catch(IOException e) {
      Util.IOErrorPrint(e, "Reading config file="+configFile, par.getPrintStream());
    }
    return null;
  } 
  /** the thread portion looks for gaps from the sender and uses the query from the
   * CWB to build up the CD1.1 records needed and writes them into the .cd11out 
   * ring file.  It then queues the blocks for out-of-band writing by the
   * sender.
   */
  @Override
  public void run() {
    int gapNumber;
    long lowSeq;
    long highSeq;
    int maxsecs=600;
    long nlap=0;
    GregorianCalendar g = new GregorianCalendar();
    StringBuilder runsb = new StringBuilder(100);
 
    try{sleep(120000);} catch(InterruptedException e) {}
    par.prta(Util.clear(runsb).append(tag).append(Util.asctime()).append(" CD11SP : start GapBuilder loop  I am ").
            append(station/*+" "+sender.getQueueSize()*/));
    int loop=0;
    long lastConfigRead=System.currentTimeMillis();
    state=1;
    while (!terminate)
    { loop=0;
      state=2;
      /*while(sender.getQueueSize() > 0) {
        if( (loop++ % 120) == 0) par.prta(tag+" Looping for queue empty "+sender.getQueueSize());
        try{(1000);} catch(InterruptedException e) {}
      }*/
      try{sleep(1);} catch(InterruptedException e) {}
      nlap++;
      state=3;
      if(nlap % 10000 == 1) par.prta(Util.clear(runsb).append(station).append(" Gap loop ").
              append(nlap).append(" ngaps=").append(sender.getGapList().getGapCount()));
      lowSeq = -1;
      highSeq = -1;
      long lseq=-1;
      long hseq=-1;
      long now =System.currentTimeMillis();
      gapNumber=0;
      state=4;
      GapList gaps = sender.getGapList();
      //synchronized(gaps) {        // allow no modifications for the time being
      try {
        state=5;
        while(gapNumber < gaps.getGapCount()) {
          if(gapNumber >= gaps.getGapCount() ) { break;}
          if(now - gaps.getLastTime(gapNumber) > 600000) {  // was maxsecs*1000 - last try for this gap was 10 minutes ago
            lowSeq = gaps.getLowSeq(gapNumber);
            highSeq = gaps.getHighSeq(gapNumber);
            gaps.setLastTime(gapNumber, now);
            break;
          }
          gapNumber++;
        }
        if(gaps.getGapCount() > gapNumber) {
          lseq = gaps.getLowSeq(gapNumber);
          hseq = gaps.getHighSeq(gapNumber);
        }
      }
      catch(RuntimeException e) {
        par.prta(Util.clear(runsb).append("CD11SP Rutime doing gap"));
        e.printStackTrace(par.getPrintStream());
        lowSeq=-1;
        highSeq=-1;
      }
      state=6;
      // We have a gap from the list that has not been processed lately the low and high will be set
      int pktsent=0;
      if(lowSeq >= highSeq) try{sleep(120000);} catch(InterruptedException e) {}// nothing to do, wait.
      else {
        state=67;
          for(long low=lowSeq; low<highSeq; low=low+60) {
            if(lowSeq > 0) {
              pktsent=sender.doFetchForGap(low,Math.min(low+60,highSeq),gaps);
              par.prta(Util.clear(runsb).append("CD11SP do gap low=").append(low).append(" ").append(lowSeq).append("-").append(highSeq).
                      append(" gap#=").append(gapNumber).append("/").append(gaps.getGapCount()).append(" ").
                      append(lseq).append("-").append(hseq).append(" ").
                      append(Util.ascdatetime2(low*10000L+CD11Frame.FIDUCIAL_START_MS)).append("-").
                      append(Util.ascdatetime2(highSeq*10000L+CD11Frame.FIDUCIAL_START_MS)).
                      append(" #pktsent=").append(pktsent));
              state = 66;
            }
            try
            { state=12;
              sleep(lowSeq > 0?Math.max(1,Math.min(pktsent,30))*1000:30000);
            }
            catch(InterruptedException e) {}
          }
      }
      state=7;
      
      if(now - lastConfigRead > 120000) {
        state=8;
        lastConfigRead=now;
        par.prta(Util.clear(runsb).append("Two minute ").append(toStringBuilder(null)).append(" sender=").append(sender.toStringBuilder(null)));
        try {
          state=9;
          String args = getConfigLine(station);
          if(!args.equals(arglineraw)) {
            par.prta(Util.clear(runsb).append(" Config Change is  : ").append(args).
                    append("\n").append(Util.asctime()).append(" Config Change was : ").append(argline));
            arglineraw=args;
            state=10;
            parseArgs(arglineraw);
          }
        }
        catch(IOException e) {
          par.prta(Util.clear(runsb).append("Wow.  Got IO error trying to read config file e=").append(e));
          e.printStackTrace(par.getPrintStream());
        }
      }
      state=11;
      if(terminate) break;
    }  
    sender.terminate();
    for(int i=0; i<30; i++) {
      if(!sender.isRunning() && !sender.isAlive()) break;
      try{sleep(1000);} catch(InterruptedException e) {}
      if(i % 10 == 0) par.prta(Util.clear(runsb).append(tag).append(" Waiting for sender to exit loop=").append(i));
    }

    par.prta(Util.clear(runsb).append(tag).append(" exiting. sender running=").
            append(sender.isRunning()).append(" ").append(sender.isAlive()));
  }

  class CD11SenderClientMonitor extends Thread {
    StringBuilder tmpsb = new StringBuilder(50);
    public CD11SenderClientMonitor () {start();}
    @Override
    public void run() {
      long lastStatus=System.currentTimeMillis();
      int lastReaderNFrames=0;
      int lastSenderNPackets=0;
      long lastSeqOutCheck = lastSeqOut;
      while(!terminate) {
        for(int i=0; i<60; i++) {try{sleep(1000);} catch(InterruptedException e) {} if(terminate) break;}
        if(System.currentTimeMillis() - lastStatus > 300000) {
          par.prta(Util.clear(tmpsb).append(tag).append("Mon: Check Dseq=").append(lastSeqOut-lastSeqOutCheck).
                  append(" ").append(sender.getStatusString()));
          if(lastSenderNPackets == sender.getNPackets() && sender.getReaderNFrames()== lastReaderNFrames &&
                  lastSeqOut != lastSeqOutCheck && sender.isConnected()) {
            par.prta(Util.clear(tmpsb).append(tag).append("Mon: *** Socket appears to be hung. restart it. sender.state=").
                    append(sender.inState()));
            sender.restart();
          }
          lastSenderNPackets = sender.getNPackets();
          lastReaderNFrames = sender.getReaderNFrames();
          lastStatus=System.currentTimeMillis();
        }
      }
      par.prta(Util.clear(tmpsb).append(tag).append(" Mon: is exitting"));
    }
    @Override
    public String toString() {return tag+" mon: lastSeqOut="+lastSeqOut;}
  }
 /** this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down.  This must cause the thread to exit
   */
  class CD11StationProcessorShutdown extends Thread {
    StringBuilder tmpsb = new StringBuilder(50);
    /** default constructor does nothing the shutdown hook starts the run() thread */
    public CD11StationProcessorShutdown() {
    }
    
    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      par.prta(Util.clear(tmpsb).append(tag).append(" Shutdown() started..."));
      terminate=true;
      try{
        ss.close();
      }
      catch(IOException e) {}
      par.prta(Util.clear(tmpsb).append(tag).append(" shutdown() is complete."));
    }
  }  
}
