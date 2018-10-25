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

package gov.usgs.anss.cd11;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;
/** This class does I/O and encodeing and decodeing of CD1.1 frames.  It was implented 
 * using version 0.3 of the document dated 18 Dec 2002.  References in here should be
 * to that document.  It can be use to load data for specific frame formats for output, but
 * is mostly used for decoding incoming frames.
 *
 * @author davidketchum
 */
public class CD11Frame {
  public static final long FIDUCIAL_START_MS = new GregorianCalendar(2000, 0, 1, 0, 0, 0).getTimeInMillis();
  public static final int FRAME_HDR_LENGTH=36;
  public static final int CONNECTION_BODY_LENGTH=32;// Body without the header
  public static final int FRAME_TRAILER_LEN=16;   // this does not include the authentication body
  
  // These are the possible frame types
  public static final int TYPE_CONNECTION_REQUEST=1;
  public static final int TYPE_CONNECTION_RESPONSE=2;
  public static final int TYPE_OPTION_REQUEST=3;
  public static final int TYPE_OPTION_RESPONSE=4;
  public static final int TYPE_DATA=5;
  public static final int TYPE_ACKNACK=6;
  public static final int TYPE_ALERT=7;
  public static final int TYPE_COMMAND_REQUEST=8;
  public static final int TYPE_COMMAND_RESPONSE=9;
  public static final int TYPE_CD1_ENCAPSULATION=13;
  public static final int MAJOR_VERSION=0;
  public static final int MINOR_VERSION=2;
  public static final String [] typeString ={"zero","ConnRqst","ConnResp","OptRqst","OptResp",
      "Data","Ack","Alert","CmdRqst","CmdResp","Ten","Eleven","Twelve","CD1Encap"};
  /* this is a fidicial date for forming sequences of out going packets from the NEIC */
    private static GregorianCalendar gc2000 = new GregorianCalendar(2000,0,1,0,0,0); 
    
  //private static DecimalFormat df2 = new DecimalFormat("00");
  //private static DecimalFormat df3 = new DecimalFormat("000");
  private EdgeThread par;              // Loggin parent
  private static boolean dbg;

  // the header portion of the frame
  private int type;
  private int trailerOffset;
  private final byte [] creator=new byte[8];
  private final byte [] destination = new byte[8];
  private long seq;
  private int series;             // The sequence series for the frame
  // The trailer portion of the frame
  private int authID;             // Authentication identifier
  private int authSize;           // size of authentication body
  private byte [] authBody;       // the authorization body, if null, no authentication
  private long commVerification;  // This is essentially the CRC
  private long commComputed;      // This is the computed CRC from when the fram was read.
  // the frame body
  private byte [] body;
  private int lenBody;
  private ByteBuffer bbody;
  // Workspace
  private byte [] b;
  private final ByteBuffer bb;
  private byte [] crcbuf;
  private final byte [] tmpcrc;
  
  // Output related fields
  private int outType;            // The output type of this frame
  private int outBodyLength;      // The length of the prepared body
  private final byte [] oCreator = new byte[8];        // Output creator
  private final byte [] oDestination = new byte[8];    // Output destination
  private long outSeq;             // Output Sequence number
  private int outSeries;          // The output series
  private int oAuthID;              // Output authID
  private final byte [] frameSet = new byte[20];        // creator:destination padded to 20 character
  private boolean dataFrameLoad;    // if true, the last load was of a data frame 
                                    //(used be getOutputBytes to know to do data frame processing)
  // Data frame related fields
  private int dataNChan;
  private int dataFrameMS;    // Data frame length in milliseconds
  private StringBuilder nominalTime = new StringBuilder(20); // The nominal time for all of the data
  private final byte [] timeScratch = new byte[20];
  private int channelStringCount;
  private final StringBuilder channelString = new StringBuilder(100);
  private int nextChannel;    // ByteBUffer bbody for position to put next channel
  private ChannelSubframe chanSubFrame;
  private final StringBuilder tmpsb = new StringBuilder(120);
  private final StringBuilder tmpsb2 = new StringBuilder(120);
  private static final StringBuilder tmpstr = new StringBuilder(100);
  /** Return number of data channels in DataFrame
   * 
   * @return # of channels
   */
  public byte [] getBufferRaw() {return b;}
  public int getDataNchan() {return dataNChan;}
  public int getDataFrameMS() {return dataFrameMS;}
  public StringBuilder getDataNominalTime() {return nominalTime;}
  public StringBuilder getChannelString() {return channelString;}
  public boolean crcOK() {return commVerification == commComputed;}
  public int getType() {return type;}
  public int getBodyLength() {return lenBody;}
  public byte [] getBody() {return body;}
  public ByteBuffer getBodyByteBuffer() {return bbody;}
  public byte [] getAuthBody() {return authBody;}
  public int getAuthSize() {return authSize;}
  public int getAuthID() {return authID;}
  public byte [] getCreator() {return creator;}
  public byte [] getDestination() {return destination;}
  public int getSeries() {return series;}
  public long getSeq() {return seq;}
  public long getOutSeq() {return outSeq;}
  private void prt(String a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prta(String a) {if(par == null) Util.prta(a); else par.prta(a);}
  private void prt(StringBuilder a) {if(par == null) Util.prt(a); else par.prt(a);}
  private void prta(StringBuilder a) {if(par == null) Util.prta(a); else par.prta(a);}
  public StringBuilder getCreatorSB() {return stringBuilderFrom(creator, 8, null);}
  public StringBuilder getDestinationSB() {return stringBuilderFrom(destination, 8, null);}
  public StringBuilder getFrameSetSB() {return stringBuilderFrom(frameSet,20, null);}
  public static long calcSequence(GregorianCalendar g) {    // A time within the 10 second interval
    return (g.getTimeInMillis() - FIDUCIAL_START_MS)/10000L;
  }
  public void setParent(EdgeThread t) {par = t;}
  public static void setDebug(boolean t) {dbg=t; if(dbg) new RuntimeException("CD11Frame is in debug").printStackTrace();}
  /** CD1.1 uses null padded strings, but Java wants space padded for convenience.  This routine
   * will return a line from a byte array with zeros/null turned to spaces
   * @param buf Buffer with data to convert to a string
   * @param len Desired sring length
   * @param tmp A string builder which will receive the String or null if one is suppose to be returned.
   * @return A string with spaces replacing the nulls
   */
  public synchronized static StringBuilder stringBuilderFrom(byte [] buf, int len, StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) sb = Util.clear(tmpstr);
    for(int i=0; i<len; i++) if(buf[i] == 0) len = i;
    Util.clear(sb);
    for(int i=0; i<len; i++) sb.append((char) buf[i]);
    return sb;
  }
  public void setType(int ty) {type=ty;}
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sbt = tmp;
    if(sbt == null) {
      sbt=Util.clear(tmpsb2);
    }
    synchronized(sbt) {
      sbt.append("CD11Frm:").append(type >=0 && type <=13?typeString[type]:"UnkwnType");
      if(type == TYPE_DATA || type == TYPE_CD1_ENCAPSULATION ) {
        sbt.append(" ").append(nominalTime).
              append(" toff=").append(trailerOffset).append(" ");
        stringBuilderFrom(creator,8, sbt);
        sbt.append(":");
        stringBuilderFrom(destination,8, sbt);
        sbt.append(" sq=").append(series).
              append("/").append(seq).append(" bLen=").append(lenBody).append("/").append(b.length).append(" aid=").append(authID).
              append(" sz=").append(authSize).append(" CRC=").append(crcOK()?"t":"f**");
      }
    }
    return sbt;
  }
  /*public String toOutputString() {
    return "CD11Frm:"+(outType >=0 && outType <=13?typeString[outType]:"UnkwnType")+
      (outType == TYPE_DATA? " "+nominalTime:"")+" toff="+(outBodyLength+FRAME_HDR_LENGTH)+
      " "+stringFrom(oCreator,8)+":"+stringFrom(oDestination,8)+" sq="+outSeries+"/"+outSeq+
      " bLen="+outBodyLength+" auth="+oAuthID;
  }*/
  public int getOutputBytesLength() {return outBodyLength;}
  /** return via the ByteBuffer given, a buffer of the last type loaded, this is the right routine for output frames
   * 
   * @param bbout A byte buffer to use in building up the data, must be long enough for header, payload and trailer
   * @return The number of bytes containing valid buffer data
   */
  public int getOutputBytes(ByteBuffer bbout) {
    // Do the Frame header per page 14 Table 3
    bbout.position(0);
    bbout.putInt(outType);
    bbout.putInt(outBodyLength+FRAME_HDR_LENGTH); // Length of packet in bytes
    bbout.put(oCreator);
    bbout.put(oDestination);
    bbout.putLong(outSeq);
    bbout.putInt(outSeries);
    // If this is a data frame, the body contains only the channel portion now, add the data frame header
    if(dataFrameLoad) {
      bbout.putInt(dataNChan);
      bbout.putInt(dataFrameMS);
      for(int i=0; i<20; i++) bbout.put((byte) nominalTime.charAt(i));
      //bbout.put(nominalTime.getBytes());
      bbout.putInt(dataNChan*10);
      for(int i=0; i<channelString.length(); i++) bbout.put((byte) channelString.charAt(i));
      //bbout.put(channelString.toString().getBytes());     // Set channel string and pad to multiple of 4
      if(channelString.length() % 4 != 0) for(int i=0; i<4-(channelString.length() % 4); i++) bbout.put((byte) 0);
    }
    // Put the body in next
    //prt("getOutputBytes bbout="+bbout+" body="+body+" outBodyLen="+outBodyLength);
    if(bbout.position()+outBodyLength > bbout.limit()) prta(" ** CD11Frame : this will overflow.  "
            +bb.position()+" bb.limit="+bbout.limit()+" len"+outBodyLength+" length="+body.length+toString());
    bbout.put(body, 0, outBodyLength);      // Add the original body to the output stream
    // If this is a data frame we need to make adjustments to body length and packet size in the header
    if(dataFrameLoad) {
      // adjust body length for data header, channelString,  and any pad bytes added to channel string
      outBodyLength += 32 + dataNChan*10+( (channelString.length() % 4) == 0? 0: 4- (channelString.length()%4));
      int pos = bbout.position();
      bbout.position(4);
      bbout.putInt(outBodyLength+FRAME_HDR_LENGTH);   // adjust packet size by added amount
      bbout.position(pos);
    }
    // Add the authentication Frame traler (table 4 pg 15)
    bbout.putInt(oAuthID);      // no key or should it be 111
    bbout.putInt(0);      // No length of auth value
    bbout.putLong(0L);    // Set CRC to zero
    long crc = CRC64.compute(bbout.array(), bbout.position());
    bbout.position(bbout.position() - 8);
    bbout.putLong(crc);
    dataFrameLoad=false;        // Its been gotten so reset the data frame flag
    return bbout.position();
  }
  /** I am not sure this routine is needed.  It seem to duplicate getOutputBytes() but using the input arguments
   * 
   * @return The bytes from the frame using the input data
   */
  public byte [] getFrameBytes() {
    byte [] buf = new byte[FRAME_HDR_LENGTH+lenBody+authSize+FRAME_TRAILER_LEN];
    ByteBuffer bbuf = ByteBuffer.wrap(buf);
    bbuf.position(0);
    bbuf.putInt(type);
    bbuf.putInt(lenBody+FRAME_HDR_LENGTH);
    bbuf.put(creator);
    bbuf.put(destination);
    bbuf.putLong(seq);
    bbuf.putInt(series);
    bbuf.put(body, 0, lenBody);
    bbuf.putInt(authID);
    bbuf.putInt(authSize);
    if(authSize > 0) bb.put(authBody,0,authSize);
    if(dbg) {
      StringBuilder sb2 = new StringBuilder(10000);
      for(int i=0; i<bbuf.position(); i++) {
        if(i % 20 == 0) sb2.append(" out ").append((i+"    ").substring(0,4)).append(":");
        sb2.append( Util.leftPad(""+buf[i],4));
        if(i % 20 == 19) sb2.append("\n");
      }
      prt(sb2);
    }
    bb.putLong(0L);                     //  CRC is computed on CRC being zero!
    bb.position(bb.position()-8);       // reposition the CRC
    bb.putLong(CRC64.compute(buf, bb.position()+8));// Calculate CRC and put it at end
    return buf;
  }
  /** This create a frame suitable for reading data
   * 
   * @param maxBody The maximum body size that can be read into this frame
   * @param maxAuthBody The maximum authorization size that can be read
   */
  public CD11Frame(int maxBody, int maxAuthBody, EdgeThread parent) {
    par = parent;
    authBody = new byte[maxAuthBody];
    body = new byte[maxBody];
    b = new byte[maxBody+FRAME_TRAILER_LEN+FRAME_HDR_LENGTH];
    bb = ByteBuffer.wrap(b);
    bbody = ByteBuffer.wrap(body);
    crcbuf = new byte[maxBody*2+FRAME_TRAILER_LEN+FRAME_HDR_LENGTH];
    tmpcrc = new byte[maxBody*2+FRAME_TRAILER_LEN+FRAME_HDR_LENGTH];
  }
  /** increment the output sequence number 
   */
  public void incOutputSeq() {outSeq++;}
  /** creat a CD11 frame designed for output, sets the creator, destination, sequence etc.
   * @param maxBody  Largest body supported by this frame
   * @param maxAuthBody The max size of the authorization section of the body
   * @param ocreator The value of the creator
   * @param odestination The value of the destination
   * @param initSeq  The initial sequence number
   * @param oseries The output series number 
   * @param auth The authID to use
   */
   public CD11Frame(int maxBody, int maxAuthBody, StringBuilder ocreator, StringBuilder odestination, long initSeq, int oseries, int auth, EdgeThread parent) {
    par = parent;
    authBody = new byte[maxAuthBody];
    body = new byte[maxBody];
    b = new byte[maxBody+FRAME_TRAILER_LEN+FRAME_HDR_LENGTH];
    bb = ByteBuffer.wrap(b);
    bbody = ByteBuffer.wrap(body);
    crcbuf = new byte[maxBody*2];
    tmpcrc = new byte[maxBody*2];
    outSeq=initSeq;
    //prt("Create output frame maxbody="+maxBody+" maxAuth="+maxAuthBody+" creator="+ocreator+" dest="+odestination+" initsq="+initSeq+" serieis="+oseries);
    setCreatorDestination(ocreator, odestination, auth);
  }
  public final void setCreatorDestination(StringBuilder ocreator, StringBuilder odestination, int auth) {
    for(int i=0; i<8; i++) {
      if(ocreator.length() > i) oCreator[i] = (byte) ocreator.charAt(i); else oCreator[i] =0;
      if(odestination.length() > i) oDestination[i] = (byte) odestination.charAt(i); else oDestination[i] =0;
      
    }
    oAuthID=auth;
    for(int i=7; i>=0; i--) {if(oCreator[i] != 32 && oCreator[i] != 0) break; else oCreator[i] =0; }
    for(int i=7; i>=0; i--) {if(oDestination[i] != 32 && oDestination[i] != 0) break; else oDestination[i] =0; }
    // Create the frame set byte array per Page 36
    Util.clear(tmpsb);
    for(int i=0; i<ocreator.length(); i++) 
      if(ocreator.charAt(i) == ' ' || ocreator.charAt(i) == 0) break; else tmpsb.append(ocreator.charAt(i));
    tmpsb.append(":");
    for(int i=0; i<odestination.length(); i++) 
      if(odestination.charAt(i) == ' ' || odestination.charAt(i) == 0) break; else tmpsb.append(odestination.charAt(i));

    //String frameset = ocreator+":"+odestination;
    //ls -prta("Setting Creator:Destination to "+tmpsb);
    for(int i=0; i<20; i++) frameSet[i] = 0;
    for(int i=0; i<tmpsb.length(); i++) frameSet[i] = (byte) tmpsb.charAt(i);
  }
  /** read fully the number of bytes, or throw exception 
   *@param in The InputStream to read from
   *@param buf The byte buffer to receive the data
   *@param off The offset into the buffer to start the read
   *@param len Then desired # of bytes
   * @throws IOException if one is encountered reading from the InputStream
   * @return The length of the read in bytes, zero if EOF is reached
   */
  public int readFully(InputStream in, byte [] buf, int off, int len) throws IOException {
    int nchar;
    int l=off;
    //if(dbg) prt("    ReadFully avail="+in.available());
    while(len > 0) {            // 
      //while(in.available() <= 0) try{Thread.sleep(10);} catch(InterruptedException e) {}
      nchar= in.read(buf, l, len);// get nchar
      //if(dbg) prt("    nchar="+nchar+" len="+len+" l="+l);
      if(nchar <= 0) {
        prta(Util.clear(tmpsb).append("FRM:").append(len).append(" read nchar=").append(nchar).
                append(" len=").append(len).append(" l=").append(l).append(" in.avail=").append(in.available()));
        return 0;
      }     // EOF - close up
      l += nchar;               // update the offset
      len -= nchar;             // reduce the number left to read
      //if(dbg) if(len > 0) try{Thread.sleep(200);} catch(InterruptedException e) {}
    }
    return l;
  }
  /** read fully the number of bytes, or throw exception 
   *@param in The InputStream to read from
   *@param buf The byte buffer to receive the data
   *@param off The offset into the buffer to start the read
   *@param len Then desired # of bytes
   * @param par A edgethread to use for logging
   * @throws IOException if one is encountered reading from the InputStream
   * @return The length of the read in bytes, zero if EOF is reached
   */
  public int readFully(InputStream in, byte [] buf, int off, int len, EdgeThread par) throws IOException {
    int nchar;
    int l=off;
    //if(dbg) prt("    ReadFully avail="+in.available());
    while(len > 0) {            // 
      //while(in.available() <= 0) try{Thread.sleep(10);} catch(InterruptedException e) {}
      nchar= in.read(buf, l, len);// get nchar
      //if(dbg) prt("    nchar="+nchar+" len="+len+" l="+l);
      if(nchar <= 0) {
        prta(Util.clear(tmpsb).append(len).append("RF read EOF nchar=").append(nchar).append(" len=").append(len).
                append(" l=").append(l).append(" off=").append(off).append(" in.avail=").append(in.available()));
        return 0;
      }     // EOF - close up
      l += nchar;               // update the offset
      len -= nchar;             // reduce the number left to read
      //if(dbg) if(len > 0) try{Thread.sleep(200);} catch(InterruptedException e) {}
    }
    return l;
  }
  byte [] crcb = new byte[8];
  byte [] first8 = new byte[8];
  byte [] hdr = new byte[CD11Frame.FRAME_HDR_LENGTH];
  /** for forwarding we need to recreate the bytes read from the connection frame
   *
   * @param out OutputStream to send byts
   * @return Length of outputed frame
   * @throws IOException If one is thrown
   */
  public int writeFrameForConnect(OutputStream out) throws IOException {
    out.write(hdr, 0, CD11Frame.FRAME_HDR_LENGTH);
    out.write(body, 0,lenBody);
    out.write(first8,0,8);
    if(authSize > 0) out.write(authBody, 0, authSize);
    out.write(crcb, 0, 8);
    return lenBody+16;
  }  /** this reads in a full frame from an InputStream and loads it into this input frame
   * 
   * @param in The InputStream to read from
   * @param par A parent which might get some log I/O
   * @return The number of bytes in the body (payload) of this frame (no headers or trailers), or minus the length if the CRC is bad
   * @throws java.io.IOException
   */

  public int readFrame(InputStream in, EdgeThread par) throws IOException {
    EdgeThread orgpar=null;
    boolean orgdbg=dbg;
    if(par != null) {
      dbg=true;
      orgpar=this.par;
      this.par = par;
    }
    int len = readFully(in, b, 0, CD11Frame.FRAME_HDR_LENGTH, par);
    System.arraycopy(b, 0, hdr, 0, CD11Frame.FRAME_HDR_LENGTH);
    if(len == 0) return 0;      // EOF, bail out
    int crcpos=0;
    System.arraycopy(b, 0, crcbuf, crcpos, len);
    crcpos += len;
    if(len != CD11Frame.FRAME_HDR_LENGTH) prta("FRM: failed to read header fully");
    bb.position(0);
    type = bb.getInt();       // this should be the frame type
    trailerOffset = bb.getInt();
    bb.get(creator);
    bb.get(destination);
    seq = bb.getLong();
    series = bb.getInt();
    lenBody = trailerOffset - FRAME_HDR_LENGTH;   // length of body payload
    for(int i=0; i<creator.length; i++) if(creator[i] == 0) creator[i] =32;
    for(int i=0; i<destination.length; i++) if(destination[i] == 0) destination[i] = 32;
    if(dbg) 
      prt(Util.clear(tmpsb).append("FRM: readframe() type=").append(type).
            append(" trailerOff=").append(trailerOffset).append(" bodyLen=").append(lenBody).
            append(" seq=").append(seq).append(" series=").append(series).
            append(" cr=").append(Util.toAllPrintable(stringBuilderFrom(creator,8, null))).
            append(" dest=").append(Util.toAllPrintable(stringBuilderFrom(destination, 8, null))));
    if(type < 0 || type > 20 || trailerOffset< 0 || trailerOffset > 10000000) {
      prt(Util.clear(tmpsb).append("FRM: frame does not make sense.  Do IOException type=").append(type).
              append(" traileroffset=").append(trailerOffset));
      throw new IOException("FRM: Hdr OOB type="+type+" trailerOffset="+trailerOffset);  
    }
        
    if(body.length < lenBody) {
      prt(Util.clear(tmpsb).append("FRM: **** (CD11FrRdBuf) Increase body buffer size to ").
              append(lenBody*2).append(" from ").append(body.length));
      SendEvent.edgeSMEEvent("CD11FrRdBuf", "CD11Frame.readFrame() had to increase buffer size for "+
              Util.toAllPrintable(stringBuilderFrom(creator, 8, null))+
        " dest="+Util.toAllPrintable(stringBuilderFrom(destination, 8, null)), this);
      body = new byte[lenBody*2];
      bbody = ByteBuffer.wrap(body);
      crcbuf = new byte[lenBody*2];

    }

    len = readFully(in, body, 0, lenBody,par);
    if(len == 0) return 0;        // EOF bail out
    System.arraycopy(body, 0, crcbuf, crcpos, lenBody);
    crcpos += lenBody;
    // Now read the trailer
    len = readFully(in, first8, 0, 8, par);        // first 8 bytes
    if(len == 0) return 0;            // EOF bail out
    System.arraycopy(first8, 0, crcbuf, crcpos, 8);
    System.arraycopy(first8, 0, b, 0, 8);
    crcpos += 8;
    bb.position(0);
    authID = bb.getInt();
    authSize = bb.getInt();
    if(dbg) prt(Util.clear(tmpsb).append("FRM: trailer authID=").append(authID).append(" size=").append(authSize));
    if(authSize > 0) {
      if(authSize > authBody.length) authBody = new byte[authSize];
      readFully(in, authBody, 0, authSize, par);
      System.arraycopy(authBody, 0, crcbuf, crcpos, authSize);
      crcpos+= authSize;
    }
    // get the CRC64 from the buffer
    len = readFully(in, crcb, 0, 8, par);
    System.arraycopy(crcb, 0, b, 0, 8);
    if(len ==0) return 0;         // EOF bail out
    bb.position(0);
    commVerification = bb.getLong();
    
    // for computing the CRC the crc positions must be zero
    for(int i=0; i< 8; i++) crcbuf[crcpos+i]=0;         // set the crc portion to zeros
    crcpos += 8;
    
    commComputed = CRC64.compute(crcbuf, crcpos);
    
    if(dbg || commVerification != commComputed) 
      prt(Util.clear(tmpsb).append("FRM:").append(crcOK()?"":"  **** ").
              append(crcOK()).append(" crc=").append(Util.toHex(commVerification)).
              append(" computed=").append(Util.toHex(commComputed)).append(" seq=").append(seq).
              append(" len=").append(crcpos));
    /*if(!crcOK()) {    // This was a debug thing to try to see if the CRC was not being calculated over the right set.
      byte [] tmp = new byte[crcpos];
      for(int off=0; off<crcpos-1; off++) {
        for(int l =crcpos-off; l>0; l--) {
          System.arraycopy(crcbuf, off, tmp, 0, l);   // copy from the original buffer
          long tcrc = CRC64.compute(tmp, l);
          if(tcrc == commVerification) prt("FRM:  **** CRC match at off="+off+" len="+l);
        }
      }
    }*/
    if(type == TYPE_DATA || type == TYPE_CD1_ENCAPSULATION) {
      // Decode parts of the data frame header
      bbody.position(0);
      dataNChan = bbody.getInt();
      dataFrameMS = bbody.getInt();   // length of time represented by this data frame
      bbody.get(timeScratch);         // get 20 bytes of time
      Util.clear(nominalTime);
      for(int i=0; i<20; i++) nominalTime.append((char) timeScratch[i]);
      //nominalTime = new String(timeScratch);
      channelStringCount = bbody.getInt();
      if(dbg) prt(Util.clear(tmpsb).append("FRM: data nchan=").append(dataNChan).
              append(" len=").append(dataFrameMS).append(" ms ").append(nominalTime).
              append(" channelStrCnt=").append(channelStringCount));
      bb.position(bb.position()+channelStringCount);
      nextChannel = bb.position();    // for decode this is the beginning of the first channel
    }
    if(dbg) prt(Util.clear(tmpsb).append("FRM: readFrame() done len=").append(lenBody));
    if(!crcOK()) return -lenBody;
    if(orgpar != null) {this.par = orgpar; dbg=orgdbg;}
    return lenBody;

  }
  /** return the nth (starting with zero) channel subframe in this data frame.  This returns
   * an internal scratch channel subframe so beware saving the result in a handle and  calling
   * this routine again.
   * 
   * @param n which frame to get starting with 0 and < DataNChan 
   * @return The decoded channel subframe or null if n is out-of-range
   */
  public ChannelSubframe getChannel(int n) {
    int point = nextChannel;
    if(n > dataNChan || n < 0) {
      prt(Util.clear(tmpsb).append("FRM: *** attempt to get channel out of range got ").
              append(n).append(" max=").append(dataNChan)); 
      return null;
    }
    // position point to the nth channel subframe
    for(int i=0; i<n; i++) {
      bbody.position(point);
      int len = bbody.getInt();
      point += len+4;           // offset to next channel
    }
    // Point is set to the next channel, build the channel
    bbody.position(point);
    if(chanSubFrame == null) chanSubFrame = new ChannelSubframe(bbody, par);
    else chanSubFrame.load(bbody);
    return chanSubFrame;
  }
  /** load this output frame with the information for a connection request
   * 
   * @param station the SSSSSCCCLL station name
   * @param type Not used, it is always TCP
   * @param ip The IP address of this node (the requestor)
   * @param port The port of the requestor
   * @throws java.net.UnknownHostException
   */
  public void loadConnectionRequest(String station, String type, String ip, int port) throws UnknownHostException {
    // Set the Frame heaer
    outType = TYPE_CONNECTION_REQUEST;
    // Set the Body of a connection request
    for(int i=0; i<body.length; i++) body[i] = 0;
    bbody.position(0);
    bbody.putShort((short) MAJOR_VERSION);   // major
    bbody.putShort((short) MINOR_VERSION);   // minor
    byte [] stbytes = (station+"        ").substring(0,8).getBytes();
    for(int i=7; i>=0; i--) 
      if(stbytes[i] == ' ') stbytes[i]=0; 
      else break;
    bbody.put(stbytes);
    // frame set 
    bbody.put(oCreator, 0,3 );
    bbody.put((byte) 0);
    byte [] idcbytes = (type+"    ").substring(0,4).getBytes();
    idcbytes[3] = 0;
    bbody.put(idcbytes);
    bbody.put(InetAddress.getByName(ip).getAddress());
    bbody.putShort((short) port);
    bbody.putInt(0);
    bbody.putShort((short) 0);
    incOutputSeq();
    outBodyLength = bbody.position();
  }
  /** load this output frame with the information for a connection request
   * 
   * @param station the SSSSSCCCLL station name
    * @throws java.net.UnknownHostException
   */
  public void loadOptionRequest(String station) throws UnknownHostException {
    // Set the Frame heaer
    outType = TYPE_OPTION_REQUEST;
    // Set the Body of a connection request
    for(int i=0; i<body.length; i++) body[i] = 0;
    bbody.position(0);
    bbody.putInt(1);        // There is one option
    bbody.putInt(1);        // It is option one
    bbody.putInt(station.trim().length()+1);
    int l = station.trim().length()+1;
    l = (l -1) / 4 * 4 +4;
    byte [] stbytes = (station+"        ").substring(0,8).getBytes();
    for(int i=7; i>=0; i--) 
      if(stbytes[i] == ' ') stbytes[i]=0; 
      else break;
    bbody.put(stbytes, 0, l);
    outBodyLength = bbody.position();
  }
  /** load this packet with correct information for a connection response.  Note the responder
   * will always be USGS and type TCP
   * 
   * @param station The station identifier used (from the connection request normally)
   * @param ipadr The IP address the receiver should open for the data connection
   * @param port The port for the data connection
   */
  public void loadConnectionResponse(String station, InetAddress ipadr, int port) {
    outType = TYPE_CONNECTION_RESPONSE;
    // Set the body of the frame
    bbody.position(0);
    bbody.putShort((short) MAJOR_VERSION);
    bbody.putShort((short) MINOR_VERSION);
    byte [] bs = (station+"        ").substring(0,8).getBytes();
    for(int i=0; i<8; i++) if( bs[i] == 32) bs[i] =0;
    bbody.put( bs);
    bbody.put( ("USGS").getBytes());      // responder type
    b = "TCP ".getBytes();
    b[3] = 0;
    bbody.put(b);
    bbody.put(ipadr.getAddress());
    bbody.putShort((short) port);
    bbody.putInt(0);                // second IP
    bbody.putShort((short) 0);      // second port
    
    outBodyLength = bbody.position();
    
  }
  //static private final StringBuilder toCD = new StringBuilder(20);
  /** convert a GregorianCalendar to CD1.1 time format YYYYDDD HH:MM:SS.MMM
   * 
   * @param g A Gregorian time to transform
   * @return The 20 character CD1.1 string
   */
  public static synchronized StringBuilder toCDTimeString(GregorianCalendar g, StringBuilder toCD) { 
    return Util.clear(toCD).append(g.get(Calendar.YEAR)).append(Util.df3z(g.get(Calendar.DAY_OF_YEAR))).
            append(" ").
            append(Util.df2z(g.get(Calendar.HOUR_OF_DAY))).append(":").
            append(Util.df2z(g.get(Calendar.MINUTE))).append(":").
            append(Util.df2z(g.get(Calendar.SECOND))).append(".").
            append(Util.df3z(g.get(Calendar.MILLISECOND)));
  }
  /** given a 20 byte CD1.1 time string, convert it to a gregorian calendar
   * @param s The 20 byte CD1.1 time string yyyyddd_hh:mm:ss.mmm
   * @param g A gregorianCalendar to set to the time from s
   */
  public static void fromCDTimeString(String s, GregorianCalendar g, Object caller) {
    if(s.length() == 19 && s.endsWith(".00")) s += "0";     // Seen this 19 with even second data
    if(s.length() < 20) {
      new RuntimeException(" **** Time String length wrong to fromCDTimeString len="+
              s.length()+"<20 s="+s+"| caller="+caller).printStackTrace();
      s +="                   ";
    } 
    try {
      int year = Integer.parseInt(s.substring(0,4));
      int doy = Integer.parseInt(s.substring(4,7));
      int hr = Integer.parseInt(s.substring(8,10));
      int min = Integer.parseInt(s.substring(11,13));
      int sec = Integer.parseInt(s.substring(14,16));
      int ms = Integer.parseInt(s.substring(17,20));
      int [] ymd = SeedUtil.ymd_from_doy(year, doy);
      g.set(year, ymd[1]-1, ymd[2], hr, min, sec);
      g.setTimeInMillis(g.getTimeInMillis()/1000L*1000L+ms);
    }
    catch(NumberFormatException e) {
      g.setTimeInMillis(86400000L);
    }
    
  }

  /** load the option request response frame, body is specified as a series of bytes
   * @param bf The byte buffer with the body
   * @param len The length of the buffer in bytes
   */
  public void loadOptionResponse(byte [] bf, int len) {
        outType = TYPE_OPTION_RESPONSE;
    // Set the body of the frame
    bbody.position(0);
    bbody.put(bf, 0, len);
    outSeq = 1;
    //incOutputSeq();
    outBodyLength = bbody.position();
    
  };
  /** load the alert frame,sent just before closing a socket, 
   * @param msg The alert message
   */
  public void loadAlert(String msg) {
        outType = TYPE_ALERT;
    // Set the body of the frame
    bbody.position(0);
    int len = msg.length();
    if(len % 4 != 0) len = len + 4 -  len % 4;
    bbody.putInt(len);
    bbody.put(msg.getBytes(), 0, msg.length());
    incOutputSeq();
    outBodyLength = bbody.position();
    
  };
  /** set this frame to be an empty data frame
   * 
   * @param nominal  The Nominal time of the frame as a GC
   * @param frameMS The time span represented by this frame im milliseconds
   */
  public void loadDataFrame(GregorianCalendar nominal, int frameMS) {
    toCDTimeString(nominal,nominalTime);
    dataNChan=0;
    dataFrameLoad=true;         // indicte that a data frame build is in progress
    dataFrameMS=frameMS;
    channelStringCount=0;
    Util.clear(channelString);
    nextChannel=0;      // position in bbody of the next channel frame
    outType = TYPE_DATA;
    outSeq = (nominal.getTimeInMillis() - gc2000.getTimeInMillis())/10000;
  }
  /** add a channel to this data frame
   * 
   * @param ssssscccll THe station,  channel and location codes
   * @param start The start time of the channel being added
   * @param nsamp Number of samples
   * @param rate The data rate for this channel
   * @param data The samples
   * @param auth If not zero, authentication is use
   * @param transformation normally 1 for canadian compression applied before signature
   * @param sensType 0=seismic, 1=hydracoustic, 2=infrasonic, 3=weather, >3 other
   */
  StringBuilder sb = new StringBuilder(100);  
  private final byte[] tmp = new byte[10];
  private final StringBuilder tsb = new StringBuilder(20);
  public synchronized StringBuilder addChannel(StringBuilder ssssscccll, GregorianCalendar start, int nsamp, double rate,
      int [] data, boolean auth, int transformation, int sensType ) {
    Util.clear(sb);
    dataNChan++;                      // Count the loaded frames
    bbody.position(nextChannel+8);    // first 4 bytes are the length and auth offset which is not yet known
    bbody.put((byte) (auth? 1:0));
    bbody.put((byte) transformation);
    bbody.put((byte) sensType);
    bbody.put((byte) 0);
    for(int i=0; i<10; i++) if(ssssscccll.length() > i) tmp[i] = (byte) ssssscccll.charAt(i); else tmp[i]=0;
    //byte [] tmp = (ssssscccll+"      ").substring(0,10).getBytes();
    for(int i=0; i<10; i++) if(tmp[i] == 32) tmp[i] = 0;        // convert spaces to nulls
    for(int i=0; i<10; i++) channelString.append((char) tmp[i]);
    //channelString.append(new String(tmp));
    channelStringCount =+10;
    bbody.put(tmp);
    bbody.put("s4".getBytes());
    //bbody.putShort((short) 0);          // The uncompressed data format
    bbody.putInt(0);        // Calib factor
    bbody.putInt(0);        // Calib period
    toCDTimeString(start,tsb);
    for(int i=0; i<tsb.length(); i++) bbody.put((byte) tsb.charAt(i));
    //bbody.put(toCDTimeString(start).getBytes());
    bbody.putInt( (int) (nsamp*1000/rate));
    bbody.putInt(nsamp);
    bbody.putInt(0);        // Status size
    sb.append("Add ").append(Util.toAllPrintable(ssssscccll)).append(" ns=").
            append(nsamp).append(" time=").append(tsb).append(" nchan=").
            append(dataNChan).append("\n");
   
    // Do the compression and store the data here!
    int len = Canada.canada_compress(bb, data, nsamp, 0);
    bbody.putInt(len);   // Size of the compressed data in bytes
    bbody.put(bb.array(), 0, len);
    if(len % 4 != 0) for(int i=0; i<4-(len%4); i++) bbody.put((byte) 0);  // pad to be on 4 byte boundary
    bbody.putInt(0);        // Subframe count from digitizer (not meaningful here!)
    int authOffset = bbody.position() - nextChannel;
    bbody.putInt(0);        // Auth key id
    bbody.putInt(0);        // Auth data size
    int nextTemp = bbody.position();
    bbody.position(nextChannel);
    bbody.putInt(nextTemp - nextChannel -4);    // length of the channel frame not counting first int
    bbody.putInt(authOffset);
    
    // position to end of record and set body length and nextChannel
    bbody.position(nextTemp);
    outBodyLength= nextTemp;
    nextChannel = nextTemp;
    if(par == null) new RuntimeException("Found null par in CD11Frame2").printStackTrace();
    if(dbg) prt(Util.clear(tmpsb).append("FRM: addChan() ").append(dataNChan).append(" ").
            append(ssssscccll).append(" authOffset=").append(authOffset).
            append(" nextChannel=").append(nextChannel));
    return sb;
  }
  /** Load an ack nac packet
   * 
   * @param lowestSeq The lowest sequence (inclusive) being acknowleged
   * @param highestSeq The highest sequence (inclusive) being acknowleg
   * @param gapLength The length of the gapbuf in bytes
   * @param gapbuf Buffer with the encoded gaps per the CD1.1 protocol manual
   */
  public void loadAckNak( long lowestSeq, long highestSeq, int gapLength, byte [] gapbuf ) {
    outType = TYPE_ACKNACK;
    // load the body with the prescribe payload
    bbody.position(0);
    bbody.put(frameSet);    // 20 character frameset
    bbody.putLong(lowestSeq); // Lowest sequence acknowleged
    bbody.putLong(highestSeq);  // Highest Sequence acknowleged
    if(bbody.position()+gapLength >= body.length) {
      prt(Util.clear(tmpsb).append("FRM: ** loadAcknack increasing buffers size to ").
              append(Math.max(body.length, bbody.position()+gapLength)*2));
      byte tmp2[] = new byte[Math.max(body.length, bbody.position()+gapLength)*2];
      System.arraycopy(body, 0, tmp2, 0, body.length);
      int pos = bbody.position();
      body = tmp2;
      bbody = ByteBuffer.wrap(body);
      bbody.position(pos);
    }
    //prta(stringFrom(oCreator,8)+":"+stringFrom(oDestination,8)+" sq="+outSeries+"/"+outSeq+
    //        "***** gapbuf.length="+gapbuf.length+" gapLength="+gapLength+" pos="+bbody.position()+
    //        " body.lenth="+body.length+" bbody.limit="+bbody.limit());
    bbody.put(gapbuf, 0, gapLength);  // The gap buffer
    outBodyLength = bbody.position();
  }
  public static void main(String [] args) {
    Util.setModeGMT();
    GregorianCalendar g = new GregorianCalendar();
    long seq = CD11Frame.calcSequence(g);
    System.out.println(""+CD11Frame.FIDUCIAL_START_MS/1000+" g.time="+g.getTimeInMillis()/1000+" "+Util.ascdatetime2(g)+" seq="+seq);
    System.exit(0);
  }
}
