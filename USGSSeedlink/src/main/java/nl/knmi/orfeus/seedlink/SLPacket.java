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
 * SLPacket.java
 *
 * Created on 06 April 2004, 11:00
 */

package nl.knmi.orfeus.seedlink;

/**Note: modified by DCK to allow reuse of SLPacket rather the creating them for
 * each one and to use USGS MiniSeed class to decode packets.  
 * The heap reaping was out of hand.  This eliminated the use of Fissures.
 *
 * @author  Anthony Lomax
 */

import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.IllegalSeednameException;
import nl.knmi.orfeus.seedlink.client.SLState;

//import java.io.*;


/**
 *
 * Class to hold and decode a SeedLink packet.
 *
 * @see edu.iris.Fissures.seed.container.Blockette
 *
 */

public class SLPacket {
    
    /** Packet type is terminated info packet. */
    public static final int TYPE_SLINFT = -101;
    /** Packet type is non-terminated info packet. */
    public static final int TYPE_SLINF = -102;
    
    /**  Terminate flag - connection was closed by the server or the termination sequence completed. */
    public static final SLPacket SLTERMINATE = new SLPacket();
    /**  No packet flag - indicates no data available. */
    public static final SLPacket SLNOPACKET = new SLPacket();
    /**  Error flag - indicates server reported an error. */
    public static final SLPacket SLERROR = new SLPacket();
    
    
    /**  SeedLink packet header size. */
    public static final int SLHEADSIZE = 8;
    /**  Mini-SEED record size. */
    public static final int SLRECSIZE = 512;
    
    /** SeedLink header signature. */
    public static String SIGNATURE = "SL";
    
    /**  SeedLink INFO packet signature. */
    public static String INFOSIGNATURE = "SLINFO";
    
    /**  SeedLink ERROR signature. */
    public static String ERRORSIGNATURE = "ERROR\r\n";
    
    /**  SeedLink END signature. */
    public static String ENDSIGNATURE = "END";
    
    
    /**  The SeedLink header */
    public byte[] slhead = null;
    
    /**  The mini-SEED record */
    public byte[] msrecord = null;
    
    
    /**  The Blockette contained in msrecord. */
    //protected Blockette blockette = null;   // remove fissures
    
    private MiniSeed ms;    // DCK: add a miniseed representation of the packet
    
    
    /**
     * Empty constructor used for internal constants
     */
    protected SLPacket() {
        
    }
    
    public MiniSeed getMiniSeed() {return ms;}
    
    /**
     * Creates a new instance of SLPacket by converting the specified subarray of bytes.
     *
     * @param bytes The bytes to be converted.
     * @param offset Index of the first byte to convert.
     *
     * @exception SeedLinkException if there are not enough bytes in the subarray
     */
    public SLPacket(byte bytes[], int offset) throws SeedLinkException {
        
        if (bytes.length - offset < SLHEADSIZE + SLRECSIZE)
            throw(new SeedLinkException("not enough bytes in subarray to construct a new SLPacket"));
        
        // SeedLink header
        slhead = new byte[SLHEADSIZE];
        System.arraycopy(bytes, offset, slhead, 0, SLHEADSIZE);
        // mini-SEED record
        msrecord = new byte[SLRECSIZE];
        System.arraycopy(bytes, offset + SLHEADSIZE, msrecord, 0, SLRECSIZE);
        try {
          if(ms == null) {
            if(msrecord[0] != 0) ms = new MiniSeed(msrecord, 0, SLRECSIZE);
          }
          else ms.load(msrecord, 0, SLRECSIZE);
        }
        catch(IllegalSeednameException e) {
          String tmp = "tmp";
        }
        
    }
    /** DCK : Add this method to allow packet reuse
     * 
     * @param bytes bytes to load
     * @param offset offset in bytes to start
     * @throws SeedLinkException 
     */
    public void reload(byte [] bytes, int offset) throws SeedLinkException {
       if (bytes.length - offset < SLHEADSIZE + SLRECSIZE)
            throw(new SeedLinkException("not enough bytes in subarray to construct a new SLPacket"));
        System.arraycopy(bytes, offset, slhead, 0, SLHEADSIZE);
        System.arraycopy(bytes, offset + SLHEADSIZE, msrecord, 0, SLRECSIZE);  
        try {
          if(ms == null) ms = new MiniSeed(msrecord, 0, SLRECSIZE);
          else ms.load(msrecord, 0, SLRECSIZE);
        }
        catch(IllegalSeednameException e) {
          String tmp="tmp";
        }
        //blockette = null;
    } // DCK: end
    /** cause the MiniSeed to be reloaded from the msrecord
     * 
     */
    public void reload() {
      try {
        if(ms == null) ms = new MiniSeed(msrecord, 0, SLRECSIZE);
        else ms.load(msrecord, 0, SLRECSIZE);
      }
      catch(IllegalSeednameException e) {
        String tmp="tmp";
      }
    }
    /**
     *
     * Check for 'SL' signature and get sequence number.
     *
     * @return the packet sequence number of this SeedLink packet on success,
     *   0 for INFO packets or -1 on error.
     *
     */
    public int getSequenceNumber() {
        
      if(SLState.compareSignature(slhead, INFOSIGNATURE.length(), INFOSIGNATURE)) 
        return 0;
      //if ((new String(slhead)).substring(0, INFOSIGNATURE.length()).equalsIgnoreCase(INFOSIGNATURE))
      //      return 0;
        
      if(SLState.compareSignature(slhead, SIGNATURE.length(), SIGNATURE)) 
        return -1;
      //  if (!(new String(slhead)).substring(0, SIGNATURE.length()).equalsIgnoreCase(SIGNATURE))
      //      return -1;+      
      int seqnum=0;
      for(int i=2; i<8; i++) {
        char hex = (char) slhead[i];
        if(Character.isDigit(hex))
          seqnum= (seqnum << 4) + (int) ((char) slhead[i] - '0');
        else if(( hex >= 'A' && hex <= 'F'))  
          seqnum = (seqnum <<4) + (int) ((char) slhead[i] - 'A' + 10);        
        else if(( hex >= 'a' && hex <= 'f'))  
          seqnum = (seqnum <<4) + (int) ((char) slhead[i] - 'a' + 10);
        else {
          gov.usgs.anss.util.Util.prta("Illegal character in sequence i="+i+" "+(char) slhead[2]+" "+
                  (char)slhead[3]+" "+(char) slhead[4]+" "+ (char) slhead[5]);
          return -1;
        }
      }
      //String tmp = new String(slhead,2,6)+" "+gov.usgs.anss.util.Util.toHex(seqnum);
      return seqnum;
      /*  String seqstr = new String(slhead, 2, 6);
        
        int seqnum = -1;
        try {
            seqnum = Integer.parseInt(seqstr, 16);
        } catch (NumberFormatException nfe) {
            System.out.println("SLPacket.getSequenceNumber(): bad packet sequence number: " + seqstr);
            return -1;
        }
        
        return(seqnum);*/
    }
    
    
    /**
     * Determines the type of packet.  First check for an INFO packet,
     * if not, assume packet contains single blockette and return its type.
     *
     * @return the packet type.
   * @throws nl.knmi.orfeus.seedlink.SeedLinkException
     */
    public int getType() throws SeedLinkException {
        
        // Check for an INFO packet
        boolean isInfo=true;
        for(int i=0; i<SLPacket.INFOSIGNATURE.length(); i++) if(slhead[i] != SLPacket.INFOSIGNATURE.charAt(i)) isInfo=false;
        if(isInfo) {
        //if ((new String(slhead)).substring(0, SLPacket.INFOSIGNATURE.length()).equalsIgnoreCase(SLPacket.INFOSIGNATURE)) {
            // Check if it is terminated
            if  (slhead[SLHEADSIZE - 1] != '*')
                return(TYPE_SLINFT);
            else
                return(TYPE_SLINF);
        }
        // The original code used byte 6 of D, Q, R, etc to mean this was data and not some other seed blockette.
        //if("DRMQPS".indexOf((char) slhead[6]) >=0 ) 
          return 999;
        //return (ms.getBlocketteType(0) > 0?ms.getBlocketteType(0):999);  // This seems to do the same thing, but Fissures returns 999 on data blocks
        // assume packet contains single blockette and return its type
        // NOTE: in libslink, the type of the first "important" blockette is returned
        //return(getBlockette().getType());
        
    }				// End of sl_packettype()
    
    
    
    /**
     *
     * Returns the Blockette contained in this SLPacket.  Creates the blockette if necessary.
     *
     * @return the blockette contained in this SeedLink packet.
     *
     * @exception SeedLinkException on error.
     *
     * @see edu.iris.Fissures.seed.container.Blockette
     * @see edu.iris.Fissures.seed.builder.SeedObjectBuilder
     * @see edu.iris.Fissures.seed.director.SeedImportDirector
     *
     */
    /*public Blockette getBlockette() throws SeedLinkException {
        
        // blockette already created
        if (this.blockette != null)
            return(blockette);
        
        
        //  create a Builder
        SeedObjectBuilder seedBuilder = new SeedObjectBuilder();
        // create the Director
        SeedImportDirector seedDirector = new SeedImportDirector(seedBuilder);
        
        try {
            
            // parse the record
            DataInputStream seedIn = new DataInputStream(new ByteArrayInputStream(msrecord));
            seedDirector.construct(seedIn);
            
            // extract first blockette
            SeedObjectContainer container = (SeedObjectContainer) seedBuilder.getContainer();
            int numElem = container.iterate(); // all Blockettes
            //System.out.println("num elements in packet = " + numElem);
            this.blockette = (Blockette) container.getNext();
            if(blockette.getType() != 999)
              seedIn=null;
            return(blockette);
            
        } catch (Exception e) {
            //e.printStackTrace();
            throw(new SeedLinkException("failed to decode mini-seed record: " + e));
        }
        
    }*/
    
    
}









