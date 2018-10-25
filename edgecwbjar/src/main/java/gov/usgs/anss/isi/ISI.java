/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.isi;

import gov.usgs.anss.edgethread.*;
import gov.usgs.anss.util.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.GregorianCalendar;

/**
 * This class represents all of the ISI parameters, commands, and status
 * functions. An IACP object handle the communications, and this class handles
 * all of the processing.
 *
 * @author davidketchum
 */
public final class ISI {

  public static final int ISI_IACP_MIN = 1000;
  public static final int ISI_IACP_REQ_SOH = 1001;      // Request SOH
  public static final int ISI_IACP_REQ_CNF = 1002;      // REQ config 
  public static final int ISI_IACP_REQ_WFDISC = 1003;   // Request WFDISC
  public static final int ISI_IACP_REQ_FORMAT = 1004;   // format part of data request
  public static final int ISI_IACP_REQ_COMPRESS = 1005; // compress part of data request
  public static final int ISI_IACP_REQ_POLICY = 1006;   // policy part of request
  public static final int ISI_IACP_REQ_TWIND = 1007;    // ime window part of data request
  public static final int ISI_IACP_SYSTEM_SOH = 1008;   // System SOH data
  public static final int ISI_IACP_STREAM_SOH = 1009;   // Stream SOH data
  public static final int ISI_IACP_WFDISC = 1010;       // WFDISC data
  public static final int ISI_IACP_STREAM_CNF = 1011;   // Stream configuration data
  public static final int ISI_IACP_GENERIC_TS = 1012;   // A ISI generic time series packet (not MiniSeed)
  public static final int ISI_IACP_RAW_PKT = 1013;      // A raw digitizer packet of arbitrary type (miniseed)
  public static final int ISI_IACP_REQ_SEQNO = 1014;    // A request based on sequence number (part of data request)
  // wildcards for time specifications
  public static final double ISI_UNDEFINED_TIMESTAMP = -1.;
  public static final double ISI_OLDEST = -2.;
  public static final double ISI_NEWEST = -3.;
  public static final double ISI_KEEPUP = -4;
  // wild cards for seqno sepecification
  public static final int ISI_OLDEST_SEQNO_SIG = 0xFFFFFFFF;
  public static final int ISI_NEWEST_SEQNO_SIG = 0xFFFFFFFE;
  public static final int ISI_KEEPUP_SEQNO_SIG = 0xFFFFFFFD;
  public static final int ISI_NEVER_SEQNO_SIG = ISI_KEEPUP_SEQNO_SIG;
  public static final int ISI_BEG_RELATIVE_SEQNO_SIG = 0xFFFFFFFC;
  public static final int ISI_END_RELATIVE_SEQNO_SIG = 0xFFFFFFFB;
  public static final int ISI_CURRENT_SEQNO_SIG = 0xFFFFFFFA;
  public static final int ISI_LARGEST_SEQNO_SIG = 0xFFFFFFF0;

  public static final String ISI_OLDEST_SEQNO_STRING = "oldest";
  public static final String ISI_NEWEST_SEQNO_STRING = "newest";
  public static final String ISI_YNGEST_SEQNO_STRING = ISI_NEWEST_SEQNO_STRING;
  public static final String ISI_KEEPUP_SEQNO_STRING = "keepup";
  public static final String ISI_NEVER_SEQNO_STRING = ISI_KEEPUP_SEQNO_STRING;

  // Data format
  public static final int ISI_FORMAT_UNDEF = 0;
  public static final int ISI_FORMAT_GENERIC = 1;
  public static final int ISI_FORMAT_NATIVE = 2;
  public static final int ISI_FORMAT_MSEED = 3;     // new and unadvertized feature

  // Tags used in the RawPacket
  public static final int ISI_TAG_EOF = 0;        // Its the last one!
  public static final int ISI_TAG_SITE_NAME = 1;
  public static final int ISI_TAG_SEQNO = 2;
  public static final int ISI_TAG_DATA_DESC = 3;
  public static final int ISI_TAG_LEN_USED = 4;
  public static final int ISI_TAG_LEN_NATIVE = 5;
  public static final int ISI_TAG_PAYLOAD = 6;
  public static final int ISI_TAG_RAW_STATUS = 7;

  private boolean first;
  private final IACP iacp;
  private final EdgeThread par;
  private ISICallBack callback;
  private final byte[] cmdbuf = new byte[1024];
  private final ByteBuffer cmd;
  private final String station;
  private String loopName;
  private boolean dbg;
  private int signature, signature2;
  private long startSeq, endSeq, sequence;
  private final String tag;

  private final byte[] scratch = new byte[4096];
  private byte[] msbuf = new byte[512];

  public void resetLink() {
    iacp.resetLink();
  }

  public void terminate() {
    iacp.terminate();
  }

  public int getSignature() {
    return signature;
  }

  public long getSequence() {
    return sequence;
  }

  public void setDebug(boolean b) {
    dbg = b;
  }

  public boolean isConnected() {
    return iacp.isConnected();
  }

  //private MiniSeed ms;                  // Used during debugging
  public void setCallBack(ISICallBack obj) {
    callback = obj;
  }

  /**
   * create new instance of an ISI link
   *
   * @param h host string to contact
   * @param p Port on that host with a ISI server
   * @param stat The station or more practically the loop name
   * @param procid The process id to use to set up the IACP
   * @param to The time out interval in seconds for the IACP
   * @param sndsize The buffer send size for the IACP
   * @param rcvsize The buffer rcv size for the IACP
   * @param throttle The throttle b/s to be enforced by the IACP
   * @param tg The uniqued tag to use for this connection
   * @param bind Bind address
   * @param parent The parent to log through.
   */
  public ISI(String h, int p, String stat, int procid, int to, int sndsize, int rcvsize,
          int throttle, String tg, String bind, EdgeThread parent) {
    iacp = new IACP(h, p, stat, procid, to, sndsize, rcvsize, throttle, bind, parent);
    iacp.setISI((ISI) this);
    tag = tg + "-ISI:";
    par = parent;
    cmd = ByteBuffer.wrap(cmdbuf);
    station = stat;
  }
  private static final GregorianCalendar gstat = new GregorianCalendar();

  public String isiTimeToString(double d) {
    long i = (long) (d * 1000. + 0.5);
    gstat.setTimeInMillis(i);
    return Util.ascdate(gstat) + " " + Util.asctime2(gstat);
  }

  /**
   * This call back routine is called by the IACP handler with each packet
   * targeted to ISI (payloadID 1000-1999)
   *
   * @param payloadID The payload ID
   * @param buf The byteBuffer with the data packet
   * @param len THe length of the data in buf
   */
  public void doISI(int payloadID, ByteBuffer buf, int len) {
    switch (payloadID) {
      case ISI_IACP_RAW_PKT:
        doRawPacket(buf, len);
        break;
      case ISI_IACP_SYSTEM_SOH:
        par.prta(tag + "ISI_IACP_SYSTEM_SOH is not yet implemented");
        break;
      case ISI_IACP_STREAM_SOH:
        buf.position(IACP.IACP_DATA_PREAMBLE_LEN);
        buf.get(scratch, 0, 12);
        String stat = new String(scratch, 0, 12);
        stat = (stat.substring(0, stat.indexOf(0)) + "     ").substring(0, 5) + stat.substring(7, 12);
        double oldestTime = buf.getDouble();
        int timeStatus = buf.getShort();
        double newTime = buf.getDouble();
        int timenewStatus = buf.getShort();
        double timeSince = buf.getDouble();
        int ndataSegments = buf.getInt();
        int ndataRecords = buf.getInt();
        par.prta("STREAM_SOH stat=" + stat + " old=" + isiTimeToString(oldestTime) + " " + Util.toHex(timeStatus)
                + " new=" + isiTimeToString(newTime) + " " + Util.toHex(timenewStatus) + " since=" + timeSince
                + " #dataSeg=" + ndataSegments + " #dataRec=" + ndataRecords);
        break;
      case ISI_IACP_GENERIC_TS:
        par.prta(Util.ascdate() + " " + tag + "ISI_IACP_GENERIC_TS is not yet implemented");
        break;
      case ISI_IACP_STREAM_CNF:
        par.prta(Util.ascdate() + " " + tag + "ISI_IACP_STREAM_CNF is not yet implemented");
        break;
      case ISI_IACP_WFDISC:
        par.prta(Util.ascdate() + " " + tag + "ISI_IACP_WFDISC is not yet implemented");
        break;
      case ISI_IACP_REQ_FORMAT:
        buf.position(IACP.IACP_DATA_PREAMBLE_LEN);
        par.prta(Util.ascdate() + " " + tag + "ISI_IACP_REQ_FORMAT is " + buf.getInt() + " (0=generic, 1=native)");
        break;
      case ISI_IACP_REQ_COMPRESS:
        buf.position(IACP.IACP_DATA_PREAMBLE_LEN);
        par.prta(Util.ascdate() + " " + tag + "ISI_IACP_REQ_COMPRESS is " + buf.getInt() + " (1=uncompress, 2=IDA 1st diff, 3=Steim1, 4=Steim2, 5= gzip)");
        break;
      case ISI_IACP_REQ_POLICY:

        par.prta(Util.ascdate() + " " + tag + "ISI_IACP_REQ_POLICY is not yet implemented len=" + len);
        if (len >= 4) {
          par.prta(Util.ascdate() + " " + tag + " ISI_IACP_POLICY value is=" + buf.getInt());
        }
        break;
      case ISI_IACP_REQ_SEQNO:
        buf.position(IACP.IACP_DATA_PREAMBLE_LEN);
        buf.get(scratch, 0, 7);
        int l;
        for (l = 0; l < 7; l++) {
          if (scratch[l] == 0) {
            break;
          }
        }

        String st = new String(scratch, 0, l);
        par.prta(Util.ascdate() + " " + tag + "ISI_IACP_REQ_SEQNO " + st + " start " + buf.getInt() + "/" + buf.getLong() + " to " + buf.getInt() + "/" + buf.getLong()
                + " -1=oldest, -2=newest, -3=keepup/continuous)");
        break;
      default:
        par.prt(Util.ascdate() + " " + tag + "Unknown ISI packet received =" + payloadID);

    }
  }

  public void doRawPacket(ByteBuffer buf, int len) {
    buf.position(IACP.IACP_DATA_PREAMBLE_LEN);      // position to beginningof packet
    int off = 0;
    String stat;
    byte compression = 0;
    byte type = 0;
    byte order = 0;
    byte sampleSize = 0;
    //int dataDesc=0;
    int lenUsed;
    int lenNative = 0;
    int status;
    while (off < len) {
      int isitag = buf.getInt();         // This is a tag
      if (isitag == ISI_TAG_EOF) {
        break;
      }
      int flen = buf.getInt();          // The length of this field
      int position = buf.position();
      if (dbg) {
        par.prt(tag + "Start decode at off=" + off + "/" + len + " pos=" + position + " tag=" + isitag + " flen=" + flen);
      }
      OUTER:
      switch (isitag) {
        case ISI_TAG_SITE_NAME:
          buf.get(scratch, 0, 7);
          stat = new String(scratch, 0, 7);
          if (dbg) {
            par.prta(tag + "ISI_TAG_SITE_NAME=" + stat);
          }
          break;
        case ISI_TAG_SEQNO:
          signature = buf.getInt();
          sequence = buf.getLong();
          if (dbg) {
            par.prta(tag + "ISI_TAG_SEQNO sig=" + signature + " seq=" + sequence);
          }
          if (first) {
            callback.isiInitialSequence(signature, sequence);
            first = false;
          }
          break;
        case ISI_TAG_DATA_DESC:
          compression = buf.get();
          type = buf.get();
          order = buf.get();
          sampleSize = buf.get();
          if (dbg) {
            par.prta(tag + "ISI_TAG+DATA_DESC compression=" + compression + " type=" + type + " (MSEED=18, IDA10.4=12) order=" + order + " sampSize=" + sampleSize);
          }
          break;
        case ISI_TAG_LEN_USED:
          lenUsed = buf.getInt();
          if (dbg) {
            par.prta(tag + "ISI_TAG_LEN_USED=" + lenUsed);
          }
          break;
        case ISI_TAG_LEN_NATIVE:
          lenNative = buf.getInt();
          if (dbg) {
            par.prta(tag + "ISI_TAG_LEN_NATIVE=" + lenNative);
          }
          break;
        case ISI_TAG_PAYLOAD:
          if (lenNative > msbuf.length) {
            par.prta(tag + " raw packet got odd length lenNative=" + lenNative + " buflen=" + msbuf.length);
            msbuf = new byte[lenNative];
          }
          buf.get(msbuf, 0, lenNative);
          switch (type) {
            case 18:
              callback.isiMiniSeed(msbuf, signature, sequence);
              break;
            case 12:
              // Its a IDA packet type 10
              if (msbuf[0] == 'T' && msbuf[1] == 'S') {
                if (msbuf[2] == 10) {
                  // 10.4 packet handler it
                  callback.isiIDA10(msbuf, signature, sequence, flen);
                  break OUTER;
                } else {
                  par.prt("Got IDA type not implemented " + msbuf[2] + "." + msbuf[3]);
                  break OUTER;
                }
              } else if (msbuf[0] == 'C' && msbuf[1] == 'A') {
                par.prt("Got Cal packet - not yet implemented");
                break OUTER;
              } else if (msbuf[0] == 'C' && msbuf[1] == 'F') {
                par.prt("Got Config packet - not yet implemented");
                break OUTER;
              } else if (msbuf[0] == 'L' && msbuf[1] == 'M') {
                buf.position(50);
                int nc = buf.getShort();
                String s = new String(msbuf, 52, nc - 52);
                par.prt(" LOG PAcket from IDA 10 :" + s);
                break OUTER;
              } else {
                par.prt("Unknown packet type 12 " + ((char) msbuf[0]) + " " + ((char) msbuf[1]));
              }
              break;
            default:
              par.prta(tag + "RawPacket got non miniseed payload comp=" + compression + " type=" + type + " (MSEED=18) ord=" + order + " sampSize=" + sampleSize + " flen=" + flen);
              par.prta(msbuf[0] + " " + msbuf[1] + " " + msbuf[2] + " " + msbuf[3] + " " + msbuf[4] + " " + msbuf[5] + " "
                      + msbuf[6] + " " + msbuf[7] + " " + msbuf[8] + " " + msbuf[9] + " " + msbuf[10] + " " + msbuf[11] + "\n"
                      + msbuf[12] + " " + msbuf[13] + " " + msbuf[14] + " " + msbuf[15] + " " + msbuf[16] + " " + msbuf[17] + " "
                      + msbuf[18] + " " + msbuf[19] + " " + msbuf[20] + " " + msbuf[21] + " " + msbuf[22] + " " + msbuf[23] + "\n"
                      + msbuf[24] + " " + msbuf[25] + " " + msbuf[26] + " " + msbuf[27] + " " + msbuf[28] + " " + msbuf[29] + " "
                      + msbuf[30] + " " + msbuf[31] + " " + msbuf[32] + " " + msbuf[33] + " " + msbuf[34] + " " + msbuf[35] + " ");
              break;
          }
          if (dbg) {
            par.prta(tag + "ISI_TAG_PAYLOAD len=" + flen);
          }
          break;
        case ISI_TAG_RAW_STATUS:
          status = buf.getInt();
          par.prta(tag + "ISI_TAG_RAW_STATUS=" + status);
          break;
        case 10:      // This is an unknown tag of flen=58 in the new version, do not know what this is
          if (dbg) {
            par.prta(tag + " Unknown isitag=" + isitag + " flen=" + flen);
          }
          break;
        default:
          par.prta(tag + "Got unknown tag in Raw Packet=" + isitag + " off=" + off + " len=" + len + " pos=" + buf.position());
          break;
      }
      buf.position(position + flen);
      off = position + flen;
    }
    //par.prta("Rawpkt stat="+stat+"comp="+compression+" ty="+type+
    //    " ord="+order+" sSz="+sampleSize+" len="+lenUsed+" lenN="+lenNative+" stat="+status+" "+signature+"/"+sequence);
  }

  public void sendSOHRequest() throws IOException {
    iacp.sendCommand(ISI_IACP_REQ_SOH, cmdbuf, 0);
  }

  /**
   * this starts up a request for the newest data in perpetuity
   *
   * @param sig the signature
   * @param seq The sequence to request
   * @param sig2 Upper end signature
   * @param seq2 the Upper end sequence
   * @throws IOException if the send to ISI server does so
   */
  public void sendSeqRequest(int sig, long seq, int sig2, long seq2) throws IOException {
    signature = sig;
    if (signature == 0) {
      signature = ISI_NEWEST_SEQNO_SIG;
    }
    signature2 = sig2;
    if (signature2 == 0) {
      signature2 = ISI_KEEPUP_SEQNO_SIG;
    }
    startSeq = seq;
    endSeq = seq2;
    // Send the ISI_IACP_REQ_FORMAT (type 1004)
    cmd.position(0);
    cmd.putInt(ISI_FORMAT_NATIVE);      //2 is native, and 1 is generic 
    iacp.sendCommand(ISI.ISI_IACP_REQ_FORMAT, cmdbuf, 4);
    // Send the ISI_IACP_REQ_COMPRESS
    cmd.position(0);
    cmd.putInt(1);
    iacp.sendCommand(ISI.ISI_IACP_REQ_COMPRESS, cmdbuf, 4);
    // Send the SEQUENCE_Number request
    cmd.position(0);
    byte[] st = new byte[7];
    for (int i = 0; i < 7; i++) {
      st[i] = 0;
    }
    byte[] in = station.substring(2).trim().getBytes();
    //byte [] in = station.substring(2).toLowerCase().trim().getBytes();
    System.arraycopy(in, 0, st, 0, in.length);
    cmd.put(st);
    cmd.putInt(signature);
    cmd.putLong(startSeq);
    cmd.putInt(signature2);
    cmd.putLong(endSeq);
    iacp.sendCommand(ISI.ISI_IACP_REQ_SEQNO, cmdbuf, cmd.position());
    par.prta(tag + " sendSeqRequest station=" + station.substring(2) + " sig=" + signature
            + " seq=" + startSeq + " signature2=" + signature2 + " seq=" + endSeq);
    // Done, now need to send null to get things going
    iacp.sendNull();
    first = true;
  }

  public void isiHeartbeat() {
    callback.isiHeartbeat();
  }

  /**
   * this is the callback entry from the IACP called when the connection is
   * first made
   */
  public void isiConnection() {
    callback.isiConnection();
  }

  /**
   * this is the callback endtry from IACP to pass Alerts back
   *
   * @param code The alert code
   * @param msg The alert message
   */
  public void isiAlert(int code, String msg) {
    callback.isiAlert(code, msg);
  }
}
