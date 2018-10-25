/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * this class is to emulate the building of packets from various objects. The
 * original Trinet code had many object types which built lots of little buffers
 * and combined them through various Java IO routines. This class takes the
 * vision of a packet backed by a ByteBuffer and the various things that need to
 * be added - whether raw data types, or the more object wrapped fields - are
 * just method calls here which build up by adding to the ByteBuffer.
 * <p>
 * The packet has a 5 int header :
 * <pre>
 * version int =1
 * msgtype int
 * pkt#  int
 * total#Packets int  You need to know the packet load in advance!
 * DataLen      Does not include this header
 *
 * Following this are datafields which consist of :
 * int    DataFieldDatalLen (fieldLen below + 8 for the field type and field length)
 * int    field type  1=int, 2=double, 3=string, 4=byte array
 * int    fieldLen    4 for int, 8 for double, some number for string or bytes
 * byte[] payload     with data values so for an int 4 bytes, for a double 8 bytes
 *
 * The above 4 fields can repeat as necessary
 * </pre>
 */
public final class PacketBuilder {

  private final EdgeThread par;
  public final int MAX_PACKET_BYTES = 16366;  // This came out of the Trinet code
  // This is the byte buffer for accumulating the packet and the byte buffer which wraps it
  private final byte[] buf = new byte[MAX_PACKET_BYTES];
  ByteBuffer bb;
  // These next 5 are the overall packet header for exchange of data on the wire
  private final int version = 1;
  private int messageType;     // this is one of the 8 command/response types from TrinetServer
  private int totalPackets;    // The
  private int packetNumber;
  private int packetLength;  // does not include header

  // these are populated by getNextField() - that is each call updates these values so 
  // the next field is available from the getters after the call to next field.
  private int totalFieldLength;
  private int fieldType;
  private int fieldLength;
  private final byte[] fieldBytes = new byte[550];
  private int fieldInt;
  private double fieldDouble;
  private int byteBufferPosition;
  private int byteBufferPayloadPosition;
  private final StringBuilder fieldString = new StringBuilder(200); //scratch space for turning bytes in a field into a String   
  // Getters for fields maintained by getNextField();

  public int getFieldLength() {
    return fieldLength;
  }

  public int getFieldType() {
    return fieldType;
  }

  public byte[] getFieldBytes() {
    return fieldBytes;
  }

  public StringBuilder getFieldStringBuilder() {
    return fieldString;
  }

  public String getFieldString() {
    return fieldString.toString();
  }

  public int getFieldInt() {
    return fieldInt;
  }

  public double getFieldDouble() {
    return fieldDouble;
  }

  @Override
  public String toString() {
    return "pkt : type=" + messageType + " " + packetNumber + " of " + totalPackets + " pkts len=" + packetLength + " position=" + bb.position();
  }

  // Getters and setters for the overall packet header
  /**
   * Set the overall packet message type - this must be one of the types defined
   * in org.trinet.waveserver.rt.TrinetTCPMessageType like TN_TCP_GETDATA_REQ or
   * TN_TCP_GETDATA_RESP
   *
   * @param type Type to set
   */
  public void setMessageType(int type) {
    messageType = type;
  }

  /**
   * set the total number of packets in the whole packet
   *
   * @param n The total
   */
  public void setTotalPackets(int n) {
    totalPackets = n;
  }

  public void setPacketNumber(int n) {
    packetNumber = n;
  }

  public void setPacketLength(int n) {
    packetLength = n;
  }

  public int getMessageType() {
    return messageType;
  }

  public int getTotalPackets() {
    return totalPackets;
  }

  public int getPacketNumber() {
    return packetNumber;
  }

  public int getPacketLength() {
    return packetLength;
  }

  /**
   * if the user wants to decode the buffer, it needs the ByteBuffer positioned
   * to the beginning of the payload. This routine returns that ByteBuffer. The
   * user can leave the position anywhere as the field reader portion will put
   * the position to the right place on the next call
   *
   * @return
   */
  public ByteBuffer getByteBufferPayload() {
    bb.position(byteBufferPayloadPosition);
    return bb;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }
  // Get information about packet

  public byte[] getBuf() {
    return buf;
  }

  public int getPosition() {
    return bb.position();
  }

  /**
   * This resets a Packet to an initial state - no data fields, no numbers or
   * lengths
   */
  public void reset() {
    packetNumber = 0;
    messageType = 0;
    packetLength = 0;
    totalPackets = 0;
    bb.position(20);
  }

  /**
   * This is used to rebuild the 5 header ints just before sending the packet.
   * It sets the response type, the # of this packet, the total # of packets.
   * The length of the packet comes from the current position of the ByteBuffer
   *
   * @param msgType The message type
   * @param pktNumber The packet number
   * @param pktTot The total number of packets
   */
  public void rebuildHeader(int msgType, int pktNumber, int pktTot) {
    int pos = bb.position();    // Save position of the byte buffer
    totalPackets = pktTot;
    packetNumber = pktNumber;
    packetLength = pos - 20;
    messageType = msgType;
    bb.position(0);
    bb.putInt(version);
    bb.putInt(messageType);
    bb.putInt(packetNumber);
    bb.putInt(totalPackets);
    bb.putInt(pos - 20);
    bb.position(pos);
  }

  /**
   * This make an empty packet builder
   *
   * @param parent A parent EdgeThread for logging
   */
  public PacketBuilder(EdgeThread parent) {
    par = parent;
    bb = ByteBuffer.wrap(buf);      // build our byte buffer

  }

  /**
   * This adds a normal MiniSeed object to the Packet being built. The trinet
   * wave server considers this a byte array packet of 524 bytes where the
   * internal structure is a bit strange :
   *
   * 536 Length of object 4 int Its a byte array 528 bytes (512 bytes of
   * MiniSeed + 8 bytes of rate + 4 bytes for number of samples + 4 bytes for
   * the 512 bytes of payload) double rate int nsamp 512 int Bytes of data which
   * follow byte [512] - The raw MiniSeed packet
   *
   * @param ms A normal MiniSeed packet to add to the Trinet data response
   * packet
   * @param trueSeconds The time as a true (including leap seconds)
   */
  public void addMiniSeed(MiniSeed ms, double trueSeconds) {
    addInt(536);      // This is the length of the object with header
    addInt(4);        // Its a byte array
    addInt(528);      // 516 bytes of the "byte array" we are faking here
    addDouble(trueSeconds); // add the start time
    addInt(ms.getNsamp());
    addInt(512);      // size of the packet
    addByteArray(ms.getBuf(), 0, 512);
  }

  /**
   * Add a channel list object as a chanel
   *
   * @param ch A ChannelList object from CWBWaveServer
   */
  /*public void addChannel(ChannelList ch) {
      addInt(104);
      addInt(4);
      addInt(96);
      
    }*/
  /**
   * This add a single double at the current position (this does not add a
   * double field - seed addDoubleField()
   *
   * @param d The double to add
   */
  public void addDouble(double d) {
    bb.putDouble(d);
  }

  /**
   * This add a single int at the current position (this does not add a int
   * field - seed addIntField()
   *
   *
   * @param i The int
   */
  public void addInt(int i) {
    bb.putInt(i);
  }

  /**
   * Add a section of bytes directory to the buffer (this does not add a byte[]
   * field - see addByteField);
   *
   * @param buffer Bytes array
   * @param off Offset to start
   * @param len Number of bytes to add starting at offset
   */
  public void addByteArray(byte[] buffer, int off, int len) {
    bb.put(buffer, off, len);
  }

  ;
    /** read a socket until a full packet is received
     * 
     * @param s The socket to read from 
     * @return Number of bytes read in packet payload (excluding the packet header)
     * @throws IOException 
     */
    public int receive(Socket s) throws IOException {
    // Read the packet header
    int len = Util.readFully(s.getInputStream(), buf, 0, 20);
    if (len == 0) {
      throw new IOException("Failed to read 20 byte header for packet before EOF!");
    }
    bb.position(0);
    int vers = bb.getInt();
    messageType = bb.getInt();
    packetNumber = bb.getInt();
    totalPackets = bb.getInt();
    packetLength = bb.getInt();
    byteBufferPosition = bb.position();
    len = Util.readFully(s.getInputStream(), buf, 20, packetLength);
    if (len != packetLength + 20) {
      throw new IOException("Failed to read full packet length before EOF!");
    }
    return packetLength;
  }

  /**
   * return this packet so this can be used like pkt.getNextField().getDouble();
   *
   * @return This object so things can be strung together
   */
  public PacketBuilder getNextField() {
    bb.position(byteBufferPosition);
    totalFieldLength = bb.getInt();
    fieldType = bb.getInt();
    fieldLength = bb.getInt();
    if (fieldLength + 8 != totalFieldLength) {
      prta("TotalFieldLength is not fieldLength+8 fieldLen=" + fieldLength + " totalLen=" + totalFieldLength);
    }
    if (fieldString.length() > 0) {
      fieldString.delete(0, fieldString.length());
    }
    byteBufferPayloadPosition = bb.position();
    switch (fieldType) {
      case 1:
        fieldInt = bb.getInt();
        break;
      case 2:
        fieldDouble = bb.getDouble();
        break;
      case 3:
        bb.get(fieldBytes, 0, fieldLength);
        for (int i = 0; i < fieldLength; i++) {
          fieldString.append((char) fieldBytes[i]);
        }
        break;
      case 4:
        bb.get(fieldBytes, 0, fieldLength);
        break;
      default:
        prta("Illegal field type = " + fieldType + " at postition=" + (bb.position() - 4));
        break;
    }
    byteBufferPosition = bb.position();
    return this;
  }

  /**
   * This adds an int field per the trinet definition. That is it adds 16 bytes
   * : 12 for the full field length, a int 1 to indicate its an int, a 4 to
   * given the length of the data payload, and the 4 bytes of the int.
   *
   * @param value int value to load
   */
  public void addIntField(int value) {
    bb.putInt(12);
    bb.putInt(1);     // its an int
    bb.putInt(4);     // 4 bytes in an int
    bb.putInt(value); // The data.
  }

  /**
   * This adds a double field per the trinet definition. That is it adds 16
   * bytes : 16 for the total field length, int 2 indicating a double, int 8 for
   * the 8 byte length of the payload and the 8 bytes for the double
   *
   * @param value double value to load
   */
  public void addDoubleField(double value) {
    bb.putInt(16);
    bb.putInt(2);     // its an int
    bb.putInt(8);     // 4 bytes in an int
    bb.putDouble(value); // The data.        
  }

  /**
   * This add bytes as a trinet byte object. That is it adds the length of the
   * bytes + 8 The len+8 for the length of this field (excluding this value), a
   * 4 to indicate byte array, the len of the byte array, and then the
   * bytes[len]
   *
   * @param b Source byte array for the bytes
   * @param off Offset in array of first byte to load
   * @param len Number of bytes to load
   */
  public void addByteField(byte[] b, int off, int len) {
    bb.putInt(len + 8);
    bb.putInt(4);       // its a byte[] field
    bb.putInt(len);
    bb.put(b, off, len);
  }

  /**
   * This add a string as a trinet string object. That is it adds the length of
   * the string + 8 The len+8 for the length of this field (excluding this
   * value), a 3 to indicate String array, the len of the string, and then the
   * string.getBytes[len]
   *
   * @param s The string to load
   */
  public void addStringField(String s) {
    int len = s.length();
    bb.putInt(len + 8);
    bb.putInt(3);       // its a byte[] field
    bb.putInt(len);

    bb.put(s.getBytes(), 0, len);
  }

  /**
   * send this packet on the given socket. The length to write is always the
   * current position of the byte buffer being used to build up the packet.
   *
   * @param s Socket to use to write
   * @throws java.io.IOException If one is thrown during the write.
   */
  public void send(Socket s) throws IOException {
    try {
      s.getOutputStream().write(buf, 0, bb.position());
    } catch (IOException e) {
      throw e;
    }
  }
}
