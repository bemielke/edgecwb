/*
 * This file is part of the ORFEUS Java Library.
 *
 * Copyright (C) 2004 Anthony Lomax <anthony@alomax.net www.alomax.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

 /*
 * SLState.java 
 *
 * Created on 05 April 2004, 10:36
 */
package nl.knmi.orfeus.seedlink.client;

/**
 * Note: modified by DCK/USGS to allow reuse of SLPacket rather the creating
 * them for each one. The heap reaping was out of hand. Also changed all tests
 * on signature to not create new string, but to compare byte by byte
 *
 * @author Anthony Lomax
 */
import nl.knmi.orfeus.seedlink.*;

/**
 *
 * Class to manage SeedLinkConnection state.
 *
 * @see edu.iris.Fissures.seed.container.Blockette
 *
 */
public class SLState {

  /**
   * Connection state down.
   */
  public static final int SL_DOWN = 0;

  /**
   * Connection state up.
   */
  public static final int SL_UP = 1;

  /**
   * Connection state data.
   */
  public static final int SL_DATA = 2;

  /**
   * Connection state.
   */
  public int state = SL_DOWN;

  /**
   * INFO query state NO_QUERY.
   */
  public static final int NO_QUERY = 0;

  /**
   * INFO query state INFO_QUERY.
   */
  public static final int INFO_QUERY = 1;

  /**
   * INFO query state KEEP_ALIVE_QUERY.
   */
  public static final int KEEP_ALIVE_QUERY = 2;

  /**
   * NFO query state.
   */
  public int query_mode = NO_QUERY;

  /**
   * Size of receiving buffer.
   */
  public static final int BUFSIZE = 8192;

  /**
   * Data buffer for received packets.
   */
  public byte[] databuf = new byte[BUFSIZE];

  /**
   * Receive pointer for databuf.
   */
  public int recptr = 0;

  /**
   * Send pointer for databuf.
   */
  public int sendptr = 0;

  /**
   * Flag to indicate if an INFO response is expected.
   */
  public boolean expect_info = false;

  /**
   * Network timeout trigger.
   */
  public int netto_trig = -1;

  /**
   * Network re-connect delay trigger.
   */
  public int netdly_trig = 0;

  /**
   * Send keepalive trigger.
   */
  public int keepalive_trig = -1;

  /**
   * Time stamp of last state update.
   */
  public double previous_time = 0.0;

  /**
   * Network timeout time stamp.
   */
  public double netto_time = 0.0;

  /**
   * Network re-connect delay time stamp.
   */
  public double netdly_time = 0.0;

  /* Keepalive time stamp. */
  public double keepalive_time = 0.0;

  /**
   *
   * Creates a new instance of SLState
   *
   */
  public SLState() {
  }

  /**
   *
   * Returns last received packet.
   *
   * @return last recieved packet if data buffer contains a full packet to send.
   *
   * @exception SeedLinkException if there is not a packet ready to send.
   *
   * @see #packetAvailable
   *
   */
  public SLPacket getPacket() throws SeedLinkException {

    if (!packetAvailable()) {
      throw (new SeedLinkException("SLPacket not available to send"));
    }

    return (new SLPacket(databuf, sendptr));

  }

  /**
   * DCK: add getPacket to fill in an preallocated SLPacket for reuse
   *
   * Returns last received packet - it can fill in a user provided packet with
   * the payload or if this is null, create a new packet.
   *
   * @param pkt The packet to fill with the data allocated by caller
   * @return The packet provided filled in or a new packet if the packet
   * provided is null
   *
   * @exception SeedLinkException if there is not a packet ready to send.
   *
   * @see #packetAvailable
   *
   */
  public SLPacket getPacket(SLPacket pkt) throws SeedLinkException {

    if (!packetAvailable()) {
      throw (new SeedLinkException("SLPacket not available to send"));
    }
    if (pkt == null) {
      return getPacket();
    }
    pkt.reload(databuf, sendptr);
    return pkt;
  }	//DCK end of new method

  /**
   *
   * Check for full packet available to send.
   *
   * @return true if data buffer contains a full packet to send.
   *
   * @see #getPacket
   *
   */
  public boolean packetAvailable() {

    return (recptr - sendptr >= SLPacket.SLHEADSIZE + SLPacket.SLRECSIZE);

  }

  /**
   *
   * Return number of bytes remaining in receiving buffer.
   *
   * @return number of bytes remaining.
   *
   */
  public int bytesRemaining() {

    return (BUFSIZE - recptr);

  }

  /**
   *
   * Check for SeedLink ERROR packet.
   *
   * @return true if next send packet is a SeedLink ERROR packet
   *
   * @exception SeedLinkException if there are not enough bytes to determine
   *
   */
  public boolean isError() throws SeedLinkException {

    if (recptr - sendptr < SLPacket.ERRORSIGNATURE.length()) {
      throw (new SeedLinkException("not enough bytes to determine packet type"));
    }
    return compareSignature(databuf, sendptr, SLPacket.ERRORSIGNATURE);
    //return((new String(databuf, sendptr, SLPacket.ERRORSIGNATURE.length())).equalsIgnoreCase(SLPacket.ERRORSIGNATURE));

  }

  public static boolean compareSignature(byte[] databuf, int sendptr, String against) {
    for (int i = 0; i < against.length(); i++) {
      if ((char) databuf[sendptr + i] != against.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   *
   * Check for SeedLink END packet.
   *
   * @return true if next send packet is a SeedLink END packet
   *
   * @exception SeedLinkException if there are not enough bytes to determine
   *
   */
  public boolean isEnd() throws SeedLinkException {

    if (recptr - sendptr < SLPacket.ENDSIGNATURE.length()) {
      throw (new SeedLinkException("not enough bytes to determine packet type"));
    }
    return compareSignature(databuf, sendptr, SLPacket.ENDSIGNATURE);
    //return((new String(databuf, sendptr, SLPacket.ENDSIGNATURE.length())).equalsIgnoreCase(SLPacket.ENDSIGNATURE));

  }

  /**
   *
   * Check for SeedLink INFO packet.
   *
   * @return true if next send packet is a SeedLink INFO packet
   *
   * @exception SeedLinkException if there are not enough bytes to determine
   * packet type
   *
   */
  public boolean packetIsInfo() throws SeedLinkException {

    if (recptr - sendptr < SLPacket.INFOSIGNATURE.length()) {
      throw (new SeedLinkException("not enough bytes to determine packet type"));
    }
    return compareSignature(databuf, sendptr, SLPacket.INFOSIGNATURE);
    //return((new String(databuf, sendptr, SLPacket.INFOSIGNATURE.length())).equalsIgnoreCase(SLPacket.INFOSIGNATURE));

  }

  /**
   *
   * Increments the send pointer by size of one packet.
   *
   */
  public void incrementSendPointer() {

    sendptr += SLPacket.SLHEADSIZE + SLPacket.SLRECSIZE;

  }

  /**
   * Temporary data buffer for packing buffer.
   */
  //private final byte[]  packed_buf = new byte[BUFSIZE];
  /**
   *
   * Packs the buffer by removing all sent packets and shifting remaining bytes
   * to beginning of buffer.
   *
   */
  public void packDataBuffer() {
    System.arraycopy(databuf, sendptr, databuf, 0, recptr - sendptr);   // Move data to begining of data buffer
    recptr -= sendptr;
    sendptr = 0;
    // This original code seems silly
    /*System.arraycopy(databuf, sendptr, packed_buf, 0, recptr - sendptr);
		byte[]  temp_buf = databuf;
		databuf = packed_buf;
		packed_buf = temp_buf;
		recptr -= sendptr;
		sendptr = 0;*/

  }

  /**
   *
   * Appends bytes to the receive buffer after the last received data.
   *
   * @param bytes incoming bytes
   * @param len Number of bytes to append
   * @throws nl.knmi.orfeus.seedlink.SeedLinkException
   *
   */
  public void appendBytes(byte[] bytes, int len) throws SeedLinkException {

    if (bytesRemaining() < len) {
      throw (new SeedLinkException("not enough bytes remaining in buffer to append new bytes"));
    }
    System.arraycopy(bytes, 0, databuf, recptr, len);
    recptr += len;
    //for (int i = 0; i < len; i++)
    //	databuf[recptr++] = bytes[i];

  }

}
