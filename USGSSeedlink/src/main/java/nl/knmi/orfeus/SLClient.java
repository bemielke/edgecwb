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
 * SLClient.java
 *
 * Created on 05 April 2004, 11:31
 */

package nl.knmi.orfeus;

/**  DCK : USGS adapt to use MiniSeed class on not Fissures.
 *
 * @author  Anthony Lomax
 */

import nl.knmi.orfeus.seedlink.*;
import nl.knmi.orfeus.seedlink.client.*;

//import edu.iris.Fissures.seed.container.*;

import java.io.*;
import java.net.*;
import gov.usgs.anss.seed.MiniSeed;



/**
 *
 * Basic class to create and use a connection to a SeedLink server using a SeedLinkServer object.
 *
 * A new SeedLink application can be created by subclassing SLClient and overriding at least
 * the packetHandler method of SLClient.
 *
 * @see nl.knmi.orfeus.seedlink.client.SeedLinkServer.
 *
 *
 *
 */

public class SLClient {
    
    
    // constants
    
    /** The full class name. */
    public static final String PACKAGE = "nl.knmi.orfeus.seedlink.client.SLClient";
    
    /** The class name. */
    public static final String CLASS_NAME = "SLClient";
    
    /** The version of this class. */
    public static final String VERSION = "1.1.0X00";
    
    public static final String VERSION_YEAR = "2005";
    public static final String VERSION_DATE = "15Apr" + VERSION_YEAR;
    public static final String COPYRIGHT_YEAR = /*"2004-" + */ VERSION_YEAR;
    
    public static final String PROGRAM_NAME = "SLClient v" + VERSION;
    public static final String VERSION_INFO = PROGRAM_NAME + " (" + VERSION_DATE + ")";
    
    public static final String[] BANNER = {
        VERSION_INFO,
        "Copyright " + (char) 169 + " " + COPYRIGHT_YEAR +
        " Anthony Lomax (www.alomax.net)",
        "SLClient comes with ABSOLUTELY NO WARRANTY"
    };
    
    
    // parameters
    
    /** SeedLinkConnection object for communicating wiht the SeedLinkServer over a socket. */
    public SeedLinkConnection slconn = null;
    
    /** Verbosity level, 0 is lowest. */
    public int verbose = 0;
    
    /** Flag to indicate show detailed packet information. */
    public boolean ppackets = false;
    
    /** Name of file containing stream list for multi-station mode. */
    public String streamfile = null;
    
    
    /** Selectors for uni-station or default selectors for multi-station. */
    public String selectors = null;
    
    /** Selectors for multi-station. */
    public String multiselect = null;
    
    /** Name of file for reading (if exists) and storing state. */
    public String statefile = null;
    
    /** Beginning of time window for read start in past. */
    // 20050415 AJL changed from Btime to String
    protected String begin_time = null;
    
    /** End of time window for reading windowed data. */
    // 20071214 AJL added
    protected String end_time = null;
    
    /** INFO LEVEL for info request only. */
    public String infolevel = null;
    
    /** Logging object. */
    public SLLog sllog = null;
    
    
    /**
     *
     *  Creates a new instance of SLClient.
     *
     */
    public SLClient() {
        
        this(null);
        
    }
    
    
    /**
     *
     *  Creates a new instance of SLClient with the specified logging object
     *
     * @param sllog logging object to handle messages.
     *
     */
    public SLClient(SLLog sllog) {
        
        for (int n = 0; n < BANNER.length; n++)
            System.out.println(BANNER[n]);
        
        this.sllog = sllog;
        
        slconn = new SeedLinkConnection(sllog);
        
    }
    
    
    /**
     *
     * Parses the commmand line arguments.
     *
     * @param args the main method arguments.
     *
     *
     * @return -1 on error, 1 if version or help argument found,  0 otherwise.
     *
     */
    public int parseCmdLineArgs(String[] args) {
        
        if (args.length < 1) {
            printUsage(false);
            return(1);
        }
        
        int optind = 0;
        
        while (optind < args.length) {
            
            if (args[optind].equals("-V")) {
                System.err.println(VERSION_INFO);
                return(1);
            }
            else if (args[optind].equals("-h")) {
                printUsage(false);
                return(1);
            }
            else if (args[optind].startsWith("-v")) {
                verbose += args[optind].length() - 1;
            }
            else if (args[optind].equals("-p")) {
                ppackets = true;
            }
            else if (args[optind].equals("-nt")) {
                slconn.setNetTimout(Integer.parseInt(args[++optind]));
            }
            else if (args[optind].equals("-nd")) {
                slconn.setNetDelay(Integer.parseInt(args[++optind]));
            }
            else if (args[optind].equals("-k")) {
                slconn.setKeepAlive(Integer.parseInt(args[++optind]));
            }
            else if (args[optind].equals("-l")) {
                streamfile = args[++optind];
            }
            else if (args[optind].equals("-s")) {
                selectors = args[++optind];
            }
            else if (args[optind].equals("-S")) {
                multiselect = args[++optind];
            }
            else if (args[optind].equals("-x")) {
                statefile = args[++optind];
            }
            else if (args[optind].equals("-t")) {
                begin_time = args[++optind];
            }
            else if (args[optind].equals("-e")) {
                end_time = args[++optind];
            }
            else if (args[optind].equals("-i")) {
                infolevel = args[++optind];
            }
            else if (args[optind].startsWith("-")) {
                System.err.println("Unknown option: " + args[optind]);
                return(-1);
            }
            else if (slconn.getSLAddress() == null) {
                slconn.setSLAddress(args[optind]);
            }
            else {
                System.err.println("Unknown option: " + args[optind]);
                return(-1);
            }
            optind++;
            
        }
        
        return(0);
        
    }
    
    
    /**
     *
     * Initializes this SLCient.
     *
     * @exception SeedLinkException on error.
     * @exception UnknownHostException if no IP address for the local host could be found.
     *
     */
    public void init() throws UnknownHostException, SeedLinkException {
        
        // Make sure a server was specified
        if (slconn.getSLAddress() == null) {
            String message = "no SeedLink server specified";
            throw(new SeedLinkException(message));
        }
        
        // Initialize the log object
        if (sllog == null)
            sllog = new SLLog(verbose, null, null, null, null);
        slconn.setLog(sllog);
        
        // Report the program version
        //sllog.log(false, 1, VERSION_INFO);
        
        // If verbosity is 2 or greater print detailed packet infor
        if ( verbose >= 2 )
            ppackets = true;
        
        // If no host is given for the SeedLink server, add 'localhost'
        if (slconn.getSLAddress().startsWith(":"))
            slconn.setSLAddress(InetAddress.getLocalHost().toString() + slconn.getSLAddress());
        
        // Load the stream list from a file if specified
        if (streamfile != null)
            slconn.readStreamList(streamfile, selectors);
        
        // Parse the 'multiselect' string following '-S'
        if (multiselect != null)
            slconn.parseStreamlist(multiselect, selectors);
        else if (streamfile == null)	// No 'streams' array, assuming uni-station mode
            slconn.setUniParams(selectors, -1, null);
        
        // Attempt to recover sequence numbers from state file
        if (statefile != null) {
            slconn.setStateFile(statefile);
        } else {
            // Set begin time for read start in past
            // 20050415 AJL added to support continuous data transfer from a time in the past
            if (begin_time != null) {
                slconn.setBeginTime(begin_time);
            }
            // Set end time for for reading windowed data
            // 20071204 AJL added
            if (end_time != null) {
                slconn.setEndTime(end_time);
            }
        }
                
        //slconn.lastpkttime = true;
        
    }
    
    
    
    /**
     *
     * Start this SLCient.
     *
     * @exception SeedLinkException on error.
     * @exception IOException if an I/O error occurs.
     *
     */
    public void run() throws Exception {
        
        if (infolevel != null)
            slconn.requestInfo(infolevel);
        
        // Loop with the connection manager
        SLPacket slpack = null;
        int count = 1;
        while ((slpack = slconn.collect(null)) != null) {
            
            if (slpack == SLPacket.SLTERMINATE)
                break;
            
            try {
                
                // do something with packet
                boolean terminate = packetHandler(count, slpack);
                if (terminate)
                    break;
                
            } catch (SeedLinkException sle) {
                System.out.print(CLASS_NAME + ": " + sle);
            }
            
            count++;
        }
        
        // Close the SeedLinkConnection
        slconn.close();
        
    }
    
    
    
    /**
     *
     * Method that processes each packet received from the SeedLinkServer.
     * This mehod should be overridded when subclassing SLClient.
     *
     * @param count the packet to process.
     * @param slpack the packet to process.
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
            return(false);
        }
        if (type == SLPacket.TYPE_SLINFT) {
            //System.out.println("Terminated INFO packet: [" + (new String(slpack.msrecord, 0, 20)) + "]");
            System.out.println("Complete INFO:\n" + slconn.getInfoString());
            if (infolevel != null)
                return(true);
            else
                return(false);
        }
        
        
        // can send an in-line INFO request here
        if (count % 100 == 0) {
            String infostr = "ID";
            slconn.requestInfo(infostr);
        }
        
        
        // if here, must be a blockette
        
        System.out.println(CLASS_NAME + ": packet seqnum: " + seqnum + ": packet type: " + type);
        
        if (!ppackets)
            return(false);
        
        //Blockette blockette = slpack.getBlockette();
        MiniSeed ms = slpack.getMiniSeed();
        
        
        //Waveform waveform = blockette.getWaveform();
        if (ms != null) {
          System.out.println(CLASS_NAME + ": blockette type: " + slpack.getType() + ": blockette: " + ms.toString());
        }
        else
            System.out.println(CLASS_NAME + ": blockette contains no waveform");
        
        return(false);
        
    }
    
    
    
    /**
     *
     * Prints the usage message for this class.
     *
     */
    public void printUsage(boolean concise) {
        System.err.println("\nUsage: java [-cp classpath] " + PACKAGE + " [options] <[host]:port>\n");
        if (concise) {
            System.err.println("Use '-h' for detailed help");
            return;
        }
        System.err.println(" ## General program options ##\n"
        + " -V             report program version\n"
        + " -h             show this usage message\n"
        + " -v             be more verbose, multiple flags can be used\n"
        + " -p             print details of data packets\n\n"
        + " -nd delay      network re-connect delay (seconds), default 30\n"
        + " -nt timeout    network timeout (seconds), re-establish connection if no\n"
        + "                  data/keepalives are received in this time, default 600\n"
        + " -k interval    send keepalive (heartbeat) packets this often (seconds)\n"
        + " -x statefile   save/restore stream state information to this file\n"
        + " -t begintime   sets a beginning time for the initiation of data transmission (year,month,day,hour,minute,second)\n"
        + " -e endtime     sets an end time for windowed data transmission  (year,month,day,hour,minute,second)\n"
        + " -i infolevel   request this INFO level, write response to std out, and exit \n"
        + "                  infolevel is one of: ID, STATIONS, STREAMS, GAPS, CONNECTIONS, ALL \n"
        + "\n"
        + " ## Data stream selection ##\n"
        + " -l listfile    read a stream list from this file for multi-station mode\n"
        + " -s selectors   selectors for uni-station or default for multi-station\n"
        + " -S streams     select streams for multi-station (requires SeedLink >= 2.5)\n"
        + "   'streams' = 'stream1[:selectors1],stream2[:selectors2],...'\n"
        + "        'stream' is in NET_STA format, for example:\n"
        + "        -S \"IU_KONO:BHE BHN,GE_WLF,MN_AQU:HH?.D\"\n"
        + "\n"
        + " <[host]:port>  Address of the SeedLink server in host:port format\n"
        + "                  if host is omitted (i.e. ':18000'), localhost is assumed\n\n");
        
    }
    
    
    /**
     *
     * main method
     *
     */
    public static void main(String[] args) {
        
        SLClient slClient = null;
        
        try {
            slClient = new SLClient();
            int rval = slClient.parseCmdLineArgs(args);
            if (rval != 0)
                System.exit(rval);
            slClient.init();
            slClient.run();
        } catch (SeedLinkException sle) {
            if (slClient != null)
                slClient.sllog.log(true, 0, sle.getMessage());
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
