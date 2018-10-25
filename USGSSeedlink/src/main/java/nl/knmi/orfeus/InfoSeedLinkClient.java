/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2004 Anthony Lomax <anthony@alomax.net>
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
 * InfoSeedLinkClient.java
 *
 * Created on 25 December 2007
 */

package nl.knmi.orfeus;

/**
 *
 * @author  Anthony Lomax
 */


import net.alomax.seistools.*;
import net.alomax.util.*;

//import nl.knmi.orfeus.*;
import nl.knmi.orfeus.seedlink.*;
//import nl.knmi.orfeus.seedlink.client.*;

import java.awt.*;
import java.util.*;

import org.dom4j.*;


public class InfoSeedLinkClient extends SLClient implements Runnable {
    
    
    public static final String[] INFO_NAMES = {"ID", "CAPABILITIES", "STATIONS", "STREAMS", "GAPS", "CONNECTIONS"};
    protected String[] infoStrings = new String[INFO_NAMES.length];
    protected Document[] infoDocuments = new Document[INFO_NAMES.length];
    
    protected static final String Z_CODES = "z_Z_u_U_c_C_d_D_o_O_0_3_6_9";
    protected static final String N_CODES = "n_N_v_V_p_P_q_Q_1_4_7_10";
    protected static final String E_CODES = "e_E_w_W_2_5_8_11";
    
    public static final String DELIM = "#";
    
    protected long timeout;		// SeedLink Connection timout in millisec
    
    protected String infoString = null;
    protected int nInfoPackets = 0;
    
    
    /** Creates a new instance of InfoSeedLinkClient */
    
    public InfoSeedLinkClient(long timeout) {
        
        this.sllog = new SLLog(1, System.out, null,  System.out, null);
        
        this.timeout = timeout;
        
    }
    
    
    /**
     *
     * Sets the host:port of the SeedLink server.
     *
     * @param sladdr the host:port of the SeedLink server.
     *
     */
    public void setSLAddress(String sladdr) {
        
        slconn.setSLAddress(sladdr);
        
    }
    
    
    
    
    
    
    /** get info level */
    
    public String getInfo(String infolevel) throws SeedLinkException {
        
        //slconn = new SeedLinkConnection(sllog);
        
        this.infolevel = infolevel;
        infoString = null;
        nInfoPackets = 0;
        
        Thread thread = new Thread(this);
        thread.start();
        
        // start timout loop
        long startTime = System.currentTimeMillis();
        while (thread.isAlive()) {
            try {
                Thread.currentThread().sleep(500);
            } catch (Exception e) {
                ;
            }
            if (System.currentTimeMillis() - startTime > timeout) {
                try {
                    slconn.terminate();
                    throw (new SeedLinkException("SeedLink Connection Timout"));
                } catch (HeadlessException ignored) {;}
                // restart timout counter
                startTime = System.currentTimeMillis();
            }
        }
        //slconn.terminate();
        slconn.close();
        
        //System.out.println("QESeedLinkClient.getInfo(): nInfoPackets = " + nInfoPackets);
        
        return(infoString);
        
    }
    
    
    public void run() {
        
        try {
            super.run();
        } catch (Exception e) {
            System.err.println("ERROR: SeedLinkClient: "+ e.getMessage());
            e.printStackTrace();
            //manager.stop(e.getMessage());
        }
        
    }
    
    
    /**
     *
     * Method that processes each packet received from the SeedLinkServer.
     * This mehod should be overridded when subclassing SLClient.
     *
     * @parem count the packet to process.
     * @parem slpack the packet to process.
     *
     * @return true if connection to SeedLink server should be closed and session terminated, false otherwise.
     *
     * @exception implementation dependent
     *
     */
    public boolean packetHandler(int count, SLPacket slpack) throws Exception {
        
        // check if not a complete packet
        if (slpack == null || slpack == SLPacket.SLNOPACKET || slpack == SLPacket.SLERROR)
            return(false);
        
        // get basic packet info
        int seqnum = slpack.getSequenceNumber();
        int type = slpack.getType();
        
        // process INFO packets here
        if (type == SLPacket.TYPE_SLINF) {
            //System.out.println("Unterminated INFO packet: [" + (new String(slpack.msrecord, 0, 20)) + "]");
            nInfoPackets++;
            return(false);
        }
        if (type == SLPacket.TYPE_SLINFT) {
            //System.out.println("Terminated INFO packet: [" + (new String(slpack.msrecord, 0, 20)) + "]");
            //System.out.println("Complete INFO:\n" + slconn.getInfoString());
            infoString = slconn.getInfoString();
            nInfoPackets++;
            if (infolevel != null)
                return(true);
            else
                return(false);
        }
        
        return(false);
        
    }
    
    
    
    
    /** return info as String */
    
    public String getInfoString(String infoLevel) throws SeedLinkException, DocumentException {
        
        // get info level index
        int index = 0;
        for ( ; index < INFO_NAMES.length; index++)
            if (infoLevel.equals(INFO_NAMES[index]))
                break;
        if (index >= INFO_NAMES.length)
            throw (new SeedLinkException("ERROR: Invalid INFO level: " + infoLevel));
        
        // check if document already loaded
        if (infoStrings[index] != null)
            return(infoStrings[index]);
        
        // get INFO level
        String infoString = getInfo(infoLevel);
        //System.out.println(infoString);
        if (infoString == null)
            throw (new SeedLinkException("ERROR: Invalid INFO response"));
        
        // save document
        infoStrings[index] = infoString;
        
        return(infoString);
        
    }
    
    
    
    
    
    
    /** return info as Document */
    
    public Document getInfoDocument(String infoLevel) throws SeedLinkException, DocumentException {
        
        // get info level index
        int index = 0;
        for ( ; index < INFO_NAMES.length; index++)
            if (infoLevel.equals(INFO_NAMES[index]))
                break;
        if (index >= INFO_NAMES.length)
            throw (new SeedLinkException("ERROR: Invalid INFO level: " + infoLevel));
        
        // check if document already loaded
        if (infoDocuments[index] != null)
            return(infoDocuments[index]);
        
        // get INFO level
        String infoString = getInfoString(infoLevel);
        
        // parse XML to String
        Document document = XMLParser.parse(infoString);
        
        // save document
        infoDocuments[index] = document;
        
        return(document);
        
    }
    
    
    
    
    
    
    /** return info as Document */
    
    public Vector getSeedChannels() throws SeedLinkException, DocumentException {
        
        String infoLevel = "STREAMS";
        Document document = getInfoDocument(infoLevel);
        
        Vector seedChannelVector = new Vector();
        Element root = document.getRootElement();
        Iterator stationIterator = root.elementIterator("station");
        Element stationElement = null;
        while (stationIterator.hasNext() && (stationElement = (Element) stationIterator.next()) != null) {
            seedChannelVector = addStreams(seedChannelVector, stationElement);
        }
        
        return(seedChannelVector);
        
    }
    
    
    
    
    /** adds streams for a station */
    
    public Vector addStreams(Vector seedChannelVector, Element stationElement) {
        
        Vector channelElementsVector = new Vector();
        
        Iterator streamIterator = stationElement.elementIterator("stream");
        Element streamElement = null;
        String currentSelector = "$$$";
        while (streamIterator.hasNext() && (streamElement = (Element) streamIterator.next()) != null) {
            // skip non-data streams
            if (!streamElement.attributeValue("type").equalsIgnoreCase("D"))
                continue;
            // get selector
            String selector = streamElement.attributeValue("location") + streamElement.attributeValue("seedname").substring(0, 2);
            if (channelElementsVector.size() < 3 && selector.equals(currentSelector))
                channelElementsVector.add(streamElement);
            else {
                if (channelElementsVector.size() > 0)
                    seedChannelVector = addChannelSet(seedChannelVector, channelElementsVector, stationElement, currentSelector);	// process channels
                channelElementsVector = new Vector();
                channelElementsVector.add(streamElement);
                currentSelector = selector;
            }
        }
        if (channelElementsVector.size() > 0)
            seedChannelVector = addChannelSet(seedChannelVector, channelElementsVector, stationElement, currentSelector);	// process channels
        
        return(seedChannelVector);
        
    }
    
    
    
    /** adds a ChannelSet */
    
    public Vector addChannelSet(Vector seedChannelVector, Vector channelElementsVector, Element stationElement, String currentSelector) {
        
        Element streamElement = (Element) channelElementsVector.elementAt(0);
        String network = stationElement.attributeValue("network");
        String staName = stationElement.attributeValue("name");
        String locName = streamElement.attributeValue("location").trim();
        
        String streamID = network + staName + "." + "__" + "_" + locName;
        
        int numTraces = channelElementsVector.size();
        if (numTraces <= 0)
            System.out.println("ERROR: " + CLASS_NAME + ": no channels in ChannelSet: " + streamID);
        else if (numTraces > 3)
            System.out.println("ERROR: " + CLASS_NAME + ": more than 3 channels in ChannelSet: " + streamID);
        
        // check available components
        String slStation = stationElement.attributeValue("network") + "_" + stationElement.attributeValue("name");
        Object[] compArray = { " ", " ", " " };
        int firstIndex = 0;
        for (int i = 0; i < numTraces; i++) {
            String comp = ((Element) channelElementsVector.elementAt(i)).attributeValue("seedname").substring(2,3);
            // find component
            if (Z_CODES.indexOf(comp) != -1) {
                compArray[0] = comp + slStation + DELIM + currentSelector + comp + ".D";
                firstIndex = i;
            } else if (N_CODES.indexOf(comp) != -1)
                compArray[1] = comp + slStation + DELIM + currentSelector + comp + ".D";
            else if (E_CODES.indexOf(comp) != -1)
                compArray[2] = comp + slStation + DELIM + currentSelector + comp + ".D";
            //else
            //    System.out.println("ERROR: " + CLASS_NAME + ": component code: " + comp + ": not recognized in TraceRef: " + streamID);
        }
        
        streamElement = (Element) channelElementsVector.elementAt(firstIndex);
        SeedChannel seedChannel = toSeedChannel(streamElement, network, staName, locName);
        
        seedChannelVector.addElement(seedChannel);
        
        return(seedChannelVector);
        
    }
    
    
    /** returns table array String representation of this ChannelSet */
    
    public SeedChannel toSeedChannel(Element channelElement, String network, String staName, String locName) {
        
        SeedChannel seedChannel = new SeedChannel();
        
        seedChannel.network = network;
        seedChannel.staName = staName;
        seedChannel.chanName = channelElement.attributeValue("seedname").substring(0, 2);
        seedChannel.compName = channelElement.attributeValue("seedname").substring(2, 3);
        seedChannel.locName = locName;
        // 2007/11/23 14:26:27.4507
        try {
            seedChannel.startTime = TimeInstant.create(channelElement.attributeValue("begin_time"), "/", " ", ":");
        } catch (Exception ignored) {;}
        try {
            seedChannel.stopTime = TimeInstant.create(channelElement.attributeValue("end_time"), "/", " ", ":");
        } catch (Exception ignored) {;}
        
        /*
         objectArray[nobj++] = channelElement.attributeValue("begin_recno");
        objectArray[nobj++] = channelElement.attributeValue("end_recno");
        objectArray[nobj++] = channelElement.attributeValue("gap_check");
        objectArray[nobj++] = channelElement.attributeValue("gap_treshold");
         **/
        
        return(seedChannel);
        
    }
    
    
    
    
    
    /**
     *
     * main method
     *
     */
    public static void main(String[] args) {
        
        InfoSeedLinkClient infoSeedLinkClient = null;
        
        try {
            long timeout = 30000;
            infoSeedLinkClient = new InfoSeedLinkClient(timeout);
            int rval = infoSeedLinkClient.parseCmdLineArgs(args);
            if (rval != 0)
                System.exit(rval);
            infoSeedLinkClient.init();
            //infoSeedLinkClient.run();
            Vector seedChannelVector = infoSeedLinkClient.getSeedChannels();
            for (int n = 0; n < seedChannelVector.size(); n++) {
                SeedChannel seedChannel = (SeedChannel) seedChannelVector.elementAt(n);
                System.out.println(seedChannel.toStringLong());
            }
        } catch (SeedLinkException sle) {
            if (infoSeedLinkClient != null)
                infoSeedLinkClient.sllog.log(true, 0, sle.getMessage());
            else {
                System.err.println("ERROR: "+ sle.getMessage());
                sle.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("ERROR: "+ e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    
}


