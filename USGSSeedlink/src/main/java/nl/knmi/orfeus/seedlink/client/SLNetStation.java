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
 * SLNetStation.java - contains network, station, configured selectors, last time and sequence.
 * DCK - USGS Changed this class to not use Btime anymore and to store last time in epoch millis.
 *  The number of Btime creations was quite high.  Does not use getters/setters.  Might be illadvised! Sep 2015
 *
 * Created on 05 April 2004, 15:49
 */
package nl.knmi.orfeus.seedlink.client;

/**
 *
 * @author Anthony Lomax
 */
import nl.knmi.orfeus.seedlink.*;

//import edu.iris.Fissures.seed.container.Btime;
//import edu.iris.Fissures.seed.exception.*;
import java.util.StringTokenizer;
import gov.usgs.anss.util.Util;

/**
 *
 * Class to hold a SeedLink stream descriptions (selectors) for a
 * network/station.
 *
 * @see edu.iris.Fissures.seed.container.Blockette
 *
 */
public class SLNetStation implements Comparable {

  /**
   * Maximum selector size.
   */
  public static int MAX_SELECTOR_SIZE = 8;

  /**
   * The network code.
   */
  //public String net = null;
  /**
   * The station code.
   */
  //public String station = null;
  public StringBuilder netstat = new StringBuilder(7);

  /**
   * SeedLink style selectors for this station.
   */
  public String selectors = null;

  /**
   * SeedLink sequence number of last packet received.
   */
  public int seqnum = -1;

  /**
   * Time stamp of last packet received.
   */
  //public Btime btime = null;
  public long ltime;

  @Override
  public int compareTo(Object obj) {
    return Util.compareTo(netstat, ((SLNetStation) obj).getNetStation());
  }

  @Override
  public String toString() {
    return netstat + " sq=" + seqnum + " " + Util.ascdatetime2(ltime);
  }    // DCK add a toString method

  /**
   * Creates a new instance of SLNetStation.
   *
   * @param nnsssss A two character and 5 character station as a StringBuilder
   * @param selectors selectors for this net/station, null if none.
   * @param seqnum SeedLink sequence number of last packet received, -1 to start
   * at the next data.
   * @param timestamp SeedLink time stamp in SEED
   * "year,day-of-year,hour,minute,second" format for last packet received, null
   * for none.
   * @throws nl.knmi.orfeus.seedlink.SeedLinkException
   *
   */
  public SLNetStation(StringBuilder nnsssss, String selectors, int seqnum, String timestamp) throws SeedLinkException {

    //this.net = net;
    //this.station = station;
    if (selectors != null) {
      this.selectors = selectors;
    }
    Util.clear(netstat).append(nnsssss);
    this.seqnum = seqnum;
    if (timestamp != null) {
      this.ltime = Util.stringToDate2(timestamp).getTime();
      /*try{
				this.ltime = new Btime(timestamp).getEpochTime();
			} catch (SeedInputException sie) {
				throw(new SeedLinkException("failed to parse timestamp: " + sie));
			}*/
    }

  }

  public StringBuilder getNetStation() {
    return netstat;
  }

  /**
   *
   * Appends a selectors String to the current selectors for this SLNetStation
   *
   * @param newSelectors Set a new selector
   * @return 0 if selectors added successfully, 1 otherwise
   *
   */
  public int appendSelectors(String newSelectors) {
    if (!selectors.contains(newSelectors)) {
      selectors += " " + newSelectors;
      return (1);
    }
    return 0;

  }

  /**
   *
   * Returns the selectors as an array of Strings. Apparently only called during
   * negotiation.
   *
   * @return array of selector Strings
   *
   */
  public String[] getSelectors() {

    try {
      StringTokenizer selTkz = new StringTokenizer(selectors);
      String[] selStrings = new String[selTkz.countTokens()];
      for (int i = 0; i < selStrings.length; i++) {
        selStrings[i] = selTkz.nextToken();
      }
      return (selStrings);
    } catch (Exception e) {
      return (new String[0]);
    }

  }

  /**
   *
   * Returns the time stamp in SeedLink string format:
   * "year,month,day,hour,minute,second"
   *
   * @return SeedLink time
   *
   */
  public StringBuilder getSLTimeStamp() {

    return gov.usgs.anss.util.Util.toDOYString(ltime);
    /*StringBuffer strbuf = new StringBuffer();
		strbuf.append(Btime.getMonthDay(btime.getYear(), btime.getDayOfYear()));
		strbuf.append(',').append(btime.getHour());
		strbuf.append(',').append(btime.getMinute());
		strbuf.append(',').append(btime.getSecond());
		
		String slTimeStr = strbuf.toString();
		slTimeStr = slTimeStr.replace('/', ',');
		slTimeStr = slTimeStr.replace(':', ',');
		
		return(slTimeStr);*/

  }

}
