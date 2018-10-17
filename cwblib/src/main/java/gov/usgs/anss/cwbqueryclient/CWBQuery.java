/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

/** This is a stub of a main() for invoking the EdgeQueryClient class using the project name
 * for this class to be slightly less confusing.  It has also been hacked to dump the UdpChannel
 * using the "-lat" class.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class CWBQuery {

  public static void main(String[] args) {
    for (String arg : args) {
      if (arg.equalsIgnoreCase("-lat")) {
        UdpChannelDump.main(args);
        System.exit(0);
      }
    }
    EdgeQueryClient.main(args);
  }
}
