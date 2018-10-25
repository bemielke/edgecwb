/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cd11;

import java.nio.ByteBuffer;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.GregorianCalendar;
import gov.usgs.anss.util.Util;
import gov.usgs.alarm.SendEvent;

/**
 * This class represents a CD1.1 channel subframe and has methods for reading that information from
 * a positioned byte buffer. The subframe is detailed in the manual as table 2.10. This class was
 * implemented based on the Version 0.3 version of the manual dated 18 Dec 2002
 *
 * @author davidketchum
 */
public class ChannelSubframe {

  private int len;
  private int authOffset;
  private byte auth;
  private byte transform;
  private byte sensorType;
  private byte optionFlag;
  private String station;       // a SSSSSCCCLL name!
  private final byte[] statbuf = new byte[10];
  private String uncompressedFormat;    // two characters
  private float calibFactor;
  private float calibPeriod;
  private String timeStamp;
  private final byte[] timebuf = new byte[20];  //scratch space to get time
  private final GregorianCalendar time = new GregorianCalendar();
  private int msLength;
  private int nsamp;
  private int statusSize;
  private byte[] status;
  private int dataSize;
  private byte[] data;
  private ByteBuffer bdata;
  private int subframeCount;
  private int authKeyID;
  private int authSize;
  private byte[] authBytes;
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final EdgeThread par;

  public int getFrameLength() {
    return len;
  }

  public int getTransform() {
    return transform;
  }

  public byte getSensorType() {
    return sensorType;
  }

  /**
   * get the station
   *
   * @return a SSSSSCCCLL station name
   */
  public String getStation() {
    return station;
  }

  public String getUncompressedFormat() {
    return uncompressedFormat;
  }

  public String getCDTimeString() {
    return timeStamp;
  }

  public GregorianCalendar getGregorianTime() {
    return time;
  }

  public int getMSLength() {
    return msLength;
  }

  public int getNsamp() {
    return nsamp;
  }

  public int getStatusSize() {
    return statusSize;
  }

  public byte[] getStatusBytes() {
    return status;
  }

  public int getDataSize() {
    return dataSize;
  }

  public byte[] getDataBytes() {
    return data;
  }

  public int getSubframeCount() {
    return subframeCount;
  }

  public double getRate() {
    return nsamp / (msLength / 1000.);
  }

  @Override
  public String toString() {
    return station + "  " + timeStamp + " " + Util.asctime2(time) + " #samp=" + nsamp + " msLen=" + msLength
            + " tfrm=" + transform + " ucfrm=" + uncompressedFormat + " sens=" + sensorType + " auth=" + auth
            + " #sta=" + statusSize + " #data=" + dataSize;
  }

  public StringBuilder toStringBuilder() {
    return Util.clear(tmpsb).append(station).append("  ").append(timeStamp).append(" ").
            append(Util.asctime2(time)).append(" #samp=").append(nsamp).
            append(" msLen=").append(msLength).append(" tfrm=").append(transform).
            append(" ucfrm=").append(uncompressedFormat).append(" sens=").append(sensorType).
            append(" auth=").append(auth).append(" #sta=").append(statusSize).append(" #data=").append(dataSize);
  }

  public ChannelSubframe(ByteBuffer b, EdgeThread parent) {
    par = parent;
    load(b);
  }

  /**
   * Load this ChannelSubframe with data from byte buffer b starting at the current position of b
   *
   * @param b A ByteBuffer position to the start of a ChannelSubframe
   */
  public final void load(ByteBuffer b) {
    // save position of beginning - These fields are in table 10 pg 23 of manual
    int pos = b.position();
    len = b.getInt();
    authOffset = b.getInt();    // 
    auth = b.get();
    transform = b.get();
    sensorType = b.get();
    optionFlag = b.get();
    b.get(statbuf);     // get 10 bytes of station name
    for (int i = 0; i < 10; i++) {
      if (statbuf[i] == 0) {
        statbuf[i] = 32;
      }
    }
    station = new String(statbuf);
    if (station.substring(5, 7).equals("sz")) {
      station = station.substring(0, 5) + "SHZ" + station.substring(8, 10);
    }
    if (station.substring(5, 7).equals("sn")) {
      station = station.substring(0, 5) + "SHN" + station.substring(8, 10);
    }
    if (station.substring(5, 7).equals("se")) {
      station = station.substring(0, 5) + "SHE" + station.substring(8, 10);
    }
    if (station.substring(5, 7).equals("bz")) {
      station = station.substring(0, 5) + "BHZ" + station.substring(8, 10);
    }
    if (station.substring(5, 7).equals("bn")) {
      station = station.substring(0, 5) + "BHN" + station.substring(8, 10);
    }
    if (station.substring(5, 7).equals("be")) {
      station = station.substring(0, 5) + "BHE" + station.substring(8, 10);
    }
    if (!(station.substring(5, 8).equals("BHZ") || station.substring(5, 8).equals("BHN") 
            || station.substring(5, 8).equals("BHE")
            || station.substring(5, 8).equals("BH1") || station.substring(5, 8).equals("BH2")
            || station.substring(5, 8).equals("SHZ") || station.substring(5, 8).equals("SHN")
            || station.substring(5, 8).equals("SHE")
            || station.substring(5, 8).equals("MHZ") || station.substring(5, 8).equals("MHN") 
            || station.substring(5, 8).equals("MHE")
            || station.substring(5, 8).equals("HHZ") || station.substring(5, 8).equals("HHN") 
            || station.substring(5, 8).equals("HHE")
            || station.substring(5, 8).equals("HH1") || station.substring(5, 8).equals("HH2")
            || station.substring(5, 8).equals("EHZ") || station.substring(5, 8).equals("EHN") 
            || station.substring(5, 8).equals("EHE")
            || station.substring(5, 8).equals("HNZ") || station.substring(5, 8).equals("HNN") 
            || station.substring(5, 8).equals("HNE")
            || station.substring(5, 8).equals("BNZ") || station.substring(5, 8).equals("BNN") 
            || station.substring(5, 8).equals("BNE")
            || station.substring(5, 8).equals("EHZ")
            || station.substring(5, 8).equals("EDH") || station.substring(5, 8).equals("BDF") 
            || station.substring(5, 8).equals("BDA")
            || station.substring(5, 8).equals("MKO") || station.substring(5, 8).equals("MDA")
            || station.substring(5, 8).equals("MWD") || station.substring(5, 8).equals("MWS")
            || station.substring(5, 8).equals("BKO") || station.substring(5, 8).equals("LEZ")
            || station.substring(5, 8).equals("LEO") || station.substring(5, 8).equals("LEZ")
            || station.substring(5, 8).equals("BWD") || station.substring(5, 8).equals("BWS")
            || station.substring(5, 8).equals("LKO") || station.substring(5, 8).equals("LDA")
            || station.substring(5, 8).equals("LEV") || station.substring(5, 8).equals("LEA")
            || station.substring(5, 8).equals("LWD") || station.substring(5, 8).equals("LWS"))) {
      // This is a hack, some data seems to have this string wrapped around wrong.  Try switching it
      String oldstation = station;
      station = oldstation.substring(4, 10) + oldstation.substring(0, 4);
      if (!(station.substring(5, 8).equals("BHZ") || station.substring(5, 8).equals("BHN") 
              || station.substring(5, 8).equals("BHE")
              || station.substring(5, 8).equals("BH1") || station.substring(5, 8).equals("BH2")
              || station.substring(5, 8).equals("SHZ") || station.substring(5, 8).equals("SHN") 
              || station.substring(5, 8).equals("SHE")
              || station.substring(5, 8).equals("MHZ") || station.substring(5, 8).equals("MHN") 
              || station.substring(5, 8).equals("MHE")
              || station.substring(5, 8).equals("HHZ") || station.substring(5, 8).equals("HHN") 
              || station.substring(5, 8).equals("HHE")
              || station.substring(5, 8).equals("HH1") || station.substring(5, 8).equals("HH2")
              || station.substring(5, 8).equals("EHZ") || station.substring(5, 8).equals("EHN") 
              || station.substring(5, 8).equals("EHE")
              || station.substring(5, 8).equals("HNZ") || station.substring(5, 8).equals("HNN") 
              || station.substring(5, 8).equals("HNE")
              || station.substring(5, 8).equals("BNZ") || station.substring(5, 8).equals("BNN") 
              || station.substring(5, 8).equals("BNE")
              || station.substring(5, 8).equals("EHZ")
              || station.substring(5, 8).equals("EDH") || station.substring(5, 8).equals("BDF") 
              || station.substring(5, 8).equals("BDA")
              || station.substring(5, 8).equals("MKO") || station.substring(5, 8).equals("MDA")
              || station.substring(5, 8).equals("MWD") || station.substring(5, 8).equals("MWS")
              || station.substring(5, 8).equals("BKO") || station.substring(5, 8).equals("LEZ")
              || station.substring(5, 8).equals("LEO") || station.substring(5, 8).equals("LEZ")
              || station.substring(5, 8).equals("BWD") || station.substring(5, 8).equals("BWS")
              || station.substring(5, 8).equals("LKO") || station.substring(5, 8).equals("LDA")
              || station.substring(5, 8).equals("LEV") || station.substring(5, 8).equals("LEA")
              || station.substring(5, 8).equals("LWD") || station.substring(5, 8).equals("LWS"))) {

        par.prt("  ****** CD1.1 channel subframe : Got bad component name=" + station + "|" 
                + station.substring(5, 8) + "|" + new String(statbuf) + "| pos=" + pos
                + "len =" + len + " aoff=" + authOffset + " auth=" + auth + " trans=" + transform 
                + " sens=" + sensorType + " option=" + optionFlag);
        int pp = b.position();
        b.position(pos);
        String s = "";
        for (int i = 0; i < 28; i++) {
          s += i + "=" + b.get() + " ";
        }
        par.prt("next=" + s);
        b.position(pp);
        SendEvent.debugSMEEvent("CD11BadCh", "Channel |" + station + "|" + oldstation 
                + "| is not a recognized component", this);
        throw new RuntimeException("Bad channel for station=" + station);
      } else {
        par.prt(" ** CD1.1 has channel subframe station in wrong order was " + oldstation 
                + " new=" + station + " and this passes!");
      }
    }
    b.get(timebuf, 0, 2);
    for (int i = 0; i < 2; i++) {
      if (timebuf[i] == 0) {
        timebuf[i] = 32;
      }
    }
    uncompressedFormat = new String(timebuf, 0, 2);
    calibFactor = b.getFloat();
    calibPeriod = b.getFloat();
    b.get(timebuf);
    timeStamp = new String(timebuf);
    CD11Frame.fromCDTimeString(timeStamp, time, this);
    msLength = b.getInt();
    nsamp = b.getInt();

    // Only the type 1 packet from table 4.22 makes any sense here
    statusSize = b.getInt();

    if (statusSize > 0) {
      try {
        if (status == null) {
          status = new byte[statusSize];
        } else if (status.length < statusSize) {
          status = new byte[statusSize];
        }
        b.get(status, 0, statusSize);
        if (statusSize % 4 != 0) {
          b.position(b.position() + 4 - (statusSize % 4));
        }
      } catch (RuntimeException e) {
        par.prt("Bad statussize=" + statusSize + " time=" + timeStamp + " calibF=" + calibFactor 
                + " calibP=" + calibPeriod + " msgLen=" + msLength + " ns=" + nsamp);
        par.prt("Runtime getting status sssize=" + statusSize + " pos=" + b.position() + " e=" + e);
        e.printStackTrace(par.getPrintStream());
        int pp = b.position();
        b.position(pos);
        String s = "";
        for (int i = 0; i < 64; i++) {
          s += i + "=" + b.get() + " ";
        }
        par.prt("next=" + s);
        b.position(pp);
        throw e;
      }

    }
    // The datasize has to be at least 8 bytes bigger than the actual data. The uncompressor often gets a long
    // when it does not need all of it and you get buffer underflow if there are not enough bytes in the backing buffer
    dataSize = b.getInt();
    if (data == null) {
      data = new byte[dataSize + 8];
      bdata = ByteBuffer.wrap(data);
    } else if (data.length < dataSize + 8) {
      data = new byte[dataSize + 8];
      bdata = ByteBuffer.wrap(data);
    }
    if (dataSize <= 0) {
      par.prt("**** ChannelSubFrame: got a load datasize <= 0! datsize=" + dataSize + " pos=" + b.position() 
              + " " + toString());
    } else {
      try {

        b.get(data, 0, dataSize);
        if (dataSize % 4 != 0) {
          b.position(b.position() + 4 - (dataSize % 4));   // i*4 align
        }
      } catch (RuntimeException e) {
        par.prt("*** ChannelSubFrame: got buffer runtime datsize=" + dataSize + " pos=" + b.position() + " e=" + e);
        e.printStackTrace(par.getPrintStream());
      }
    }
    subframeCount = b.getInt();
    authKeyID = b.getInt();
    authSize = b.getInt();
    if (authSize > 0) {
      try {
        authBytes = new byte[authSize];
        b.get(authBytes);
        if (authSize % 4 != 0) {
          b.position(b.position() + (4 - (authSize % 4))); // i*4 align
        }
      } catch (RuntimeException e) {
        par.prt("*** ChannelSubFrame: AUTH authsize=" + authSize + " pos=" + b.position() + " e=" + e);
        e.printStackTrace(par.getPrintStream());
      }

    }
    if (b.position() - pos != len + 4) // test that we are positions where the length says 
    {
      par.prt("Seem to have the wrong subframe length!");
    }
  }

  /**
   * get the data samples from this subframe, this routine does all of the decoding of various
   * allowed formats for the data.
   *
   * @param samples A user buffer to conain the samples. It must be big enough!
   * @return The number of samples decoded
   * @throws CanadaException If detected during decompression of Canadian Compressed frame
   */
  public int getSamples(int[] samples) throws CanadaException {
    bdata.position(0);
    OUTER:
    switch (transform) {
      case 0:
        // no transform, type is done by uncompressed format
        switch (uncompressedFormat) {
          case "s4":
            for (int i = 0; i < nsamp; i++) {
              samples[i] = bdata.getInt();
            }
            return nsamp;
          case "s3":
            for (int i = 0; i < nsamp; i++) {
              samples[i] = ((((int) bdata.get()) & 0xff) << 16) | ((((int) bdata.get()) & 0xff) << 8) |
                      (((int) bdata.get()) & 0xff);
            }
            return nsamp;
          case "s2":
            for (int i = 0; i < nsamp; i++) {
              samples[i] = bdata.getShort();
            }
            return nsamp;
          case "i4":
            par.prt("**** Cannot do format " + uncompressedFormat);
            break OUTER;
          case "i2":
            par.prt("**** Cannot do format " + uncompressedFormat);
            break OUTER;
          case "CD":
            par.prt("* Found CD format in uncompressed form treat as s4 " + uncompressedFormat 
                    + " trn=" + transform + " ns=" + nsamp);
            for (int i = 0; i < nsamp; i++) {
              samples[i] = bdata.getInt();
            }
            return nsamp;
          default:
            par.prt("****Cannot do format " + uncompressedFormat);
            break OUTER;
        }
      case 1:  // Canadian compression applied before signature
        if (uncompressedFormat.equals("CD")) {   // This is the CD1.0 encapsulated data
          ByteBuffer bb = ByteBuffer.wrap(data);
          bb.position(0);
          int len2 = bb.getInt();
          if (auth != 0) {
            bb.position(bb.position() + 40);  // Skip the auth bytes
          }
          double time2 = bb.getDouble();
          int ns = bb.getInt();
          int stat2 = bb.getInt();
          //par.prt("CD : "+station+" len="+len2+" datasize="+dataSize+" time="+time2+" as g ="
          //+Util.ascdatetime2((long)(time2*1000.))+" status="+Util.toHex(stat2)+" ns="+ns);

          byte[] cddata = new byte[len2 - bb.position() + 4 + 8];
          bb.get(cddata, 0, len2 - bb.position() + 4);
          Canada.canada_uncompress(cddata, samples, cddata.length - 8, nsamp, 0);
        } else {
          Canada.canada_uncompress(data, samples, dataSize, nsamp, 0);
        }
        return nsamp;
      case 2: // Canadian compression applied after signature
        Canada.canada_uncompress(data, samples, dataSize, nsamp, 0);

        break;
      case 3: // Steim compression applied before signature

        break;
      case 4: // Steim compression applied after signature
        break;
      default:
        par.prt("transformation type " + transform + " is not implemented!");
    }
    return 0;   // if we got here, the decoding failed or is not implemented.
  }
}
