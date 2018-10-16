/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

/**
 * GombergPacket.java
 *
 * This object is designed to represent a USNSN gomberg packet (communications packet). The basic
 * data is decoded into this packet and conversation from raw bytes to a GombergPacket and from a
 * GombergPacket to Raw bytes is provided. Gomberg packets are not used any more.
 *
 * Created on July 30, 2003, 9:24 AM
 *
 * @author ketchum
 */
public final class GombergPacket {

  short nbytes;
  byte routing, node, chan, pseq;
  byte[] data;
  NsnTime tc;

  /**
   * return number of bytes in communications packet
   *
   * @return Number of bytes in communications packet
   */
  public short getNbytes() {
    return nbytes;
  }

  /**
   * return routing ID (network ID) portion of the 3 byte NSN identifier
   *
   * @return the routing ID
   */
  public byte getRouting() {
    return routing;
  }

  /**
   * return node ID portion of the 3 byte NSN identifier
   *
   * @return the node ID
   */
  public byte getNode() {
    return node;
  }

  /**
   * return channel ID portion of the 3 byte NSN identifier
   *
   * @return the channel ID
   */
  public byte getChan() {
    return chan;
  }

  /**
   * return the sequence of this packet
   *
   * @return packet sequence number
   */
  public byte getSeq() {
    return pseq;
  }

  /**
   * get the raw data bytes of the dataportion of the Gomberg packet
   *
   * @return Array of bytes with the data portion of the packet
   */
  public byte[] getData() {
    return data;
  }

  /**
   * get the time of the packet as an NsnTime
   *
   * @return The NsnTime of the packets first data
   */
  public NsnTime getNsnTime() {
    return tc;
  }

  /**
   * Creates a new instance of GombergPacket from raw variables
   *
   * @param nb Number of bytes in the communications packet
   * @param route The routing ID portion of the 3 byte NSN identifier
   * @param nd The node ID portion of the 3 byte NSN identifier
   * @param ch The channel ID portion of the 3 byte NSN identifier
   * @param sq The comm link sequence number of this packet
   * @param tcin The NsnTime of the first sample in the data packet
   * @param buf The raw data buffer portion of the packet
   */
  public GombergPacket(short nb, byte route, byte nd, byte ch,
          byte sq, NsnTime tcin, byte[] buf) {
    nbytes = nb;
    routing = route;
    node = nd;
    chan = ch;
    pseq = sq;
    tc = tcin;
    data = new byte[Math.min(2048, buf.length)];
    System.arraycopy(buf, 0, data, 0, data.length);
  }

  /**
   * create a gomberg from a raw array
   *
   * @param b A raw data buffer containing a GombergPacket off the comm line
   */
  public GombergPacket(byte[] b) {
    nbytes = (short) (((int) b[2] & 0xff) + ((int) b[3] & 0xf) * 256);
    routing = b[4];
    node = b[5];
    chan = b[6];
    pseq = b[7];
    data = new byte[nbytes - 14];
    System.arraycopy(b, 8, data, 0, 6);    // put TC in data array as scratch
    tc = new NsnTime(data);
    System.arraycopy(b, 14, data, 0, nbytes - 14);
  }

  /**
   * convert this GombergPacket to a raw buffer ready for communications
   *
   * @return buffer of raw bytes representing a GombergPacket for communications
   */
  public byte[] getAsBuf() {
    byte[] b = new byte[nbytes];
    b[0] = 27;
    b[1] = 3;
    b[2] = (byte) (nbytes & 0xff);
    b[3] = (byte) (nbytes / 256);
    b[4] = routing;
    b[5] = node;
    b[6] = chan;
    b[7] = pseq;
    byte[] tcbuf = tc.getAsBuf();
    System.arraycopy(tcbuf, 0, b, 8, 6);      // Put the tc in the byte buffer at position 8
    System.arraycopy(data, 0, b, 14, nbytes - 14);
    return b;
  }

  /**
   * A string representation of this packet
   *
   * @return concatenation of nbytes, routing, node, channel, sequence, time and some data
   */
  @Override
  public String toString() {
    StringBuilder s = new StringBuilder(100);
    s.append("nb=").append(nbytes).append(" nt=").append(routing).append(" nd=").append(node).
            append(" ch=").append(chan).append(" psq=").append(pseq).append(" tc=").append(tc).append(" Dt=");
    for (int i = 0; i < Math.min(20, nbytes - 14); i++) {
      s.append(Util.toHex(data[i])).append(" ");
    }
    return s.toString();
  }

  /**
   * Unit test main
   *
   * @param args Command line args - ignored
   */
  public static void main(String[] args) {
    NsnTime tc = new NsnTime(2002, 211, 01, 34, 56, 999, 0);
    byte[] buf = new byte[2048];
    for (int i = 0; i < 2048; i++) {
      buf[i] = (byte) i;
    }

    GombergPacket gb = new GombergPacket((short) 2034, (byte) 1, (byte) 4,
            (byte) 126, (byte) 0, tc, buf);
    Util.prt("Gb =" + gb);
    buf = gb.getAsBuf();
    StringBuffer s = new StringBuffer(100);
    for (int i = 0; i < 30; i++) {
      s.append(Util.toHex(buf[i])).append(" ");
    }
    Util.prt("as buf=" + s);
    GombergPacket gb2 = new GombergPacket(buf);
    Util.prt("gb2=" + gb2);
    System.exit(0);
  }
}
