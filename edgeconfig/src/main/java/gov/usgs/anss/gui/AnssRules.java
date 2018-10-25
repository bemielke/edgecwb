/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;

import gov.usgs.anss.guidb.CommLink;
import gov.usgs.anss.util.ErrorTrack;

/**
 *
 * @author  ketchum
 */
public final class AnssRules {

  
  /** Creates a new instance of AnssRules */
  public AnssRules()
  {
  }
  
  /**
   *  Each VSAT "back haul" is associated with a known set of IP 1 or 2 byte addresses
   *  When a "commlink" of these types are chosen, this routine will validate whether the
   * choice makes sense.
   * @param ip
   * @param c
   * @param err
   */
  public static void chkIPMatchesCommlink(String ip, CommLink c, ErrorTrack err) {
    //if(ip.length() < 3) return;

    //Util.prta("chkIPMatchesCommLink ip="+ip+" commlink="+c.getCommLink()+" sub="+ip.substring(0,3));
   /* if(c.getCommLink().equals("Public") ) {
      return;
    }
    else if(c.getCommLink().equals("Usgs")) {
      return;
    }
    else if(c.getCommLink().equals("SatMex5")) {
      if(ip.substring(0,3).equals("069")) return;
      //err.set(true);
      err.appendText("Warning: IP does not match SatMex5 normal range");
    }
    else if(c.getCommLink().equals("GeSat")) {
      if(ip.substring(0,3).equals("066")) return;
      //err.set(true);
      err.appendText("Warning : IP does not match GeSat normal range");
    }
    else if(c.getCommLink().equals("G2G4R")) {
      if(ip.substring(0,7).equals("067.047") || ip.substring(0,7).equals("067.143") ||
          ip.substring(0, 7).equals("067.046")) return;
      //err.set(true);
      err.appendText("Warning : IP does not match G2G4R normal range");
    }
    else if(c.getCommLink().equals("DFC")) {
      return;
    }
    else {
      Util.prta("chkIPMatchesCommLink lacks ip="+ip+" commlink="+c.getCommLink()+" sub="+ip.substring(0,3));
      err.set(true);
      err.appendText("Commlink lacks any rules - please add");
    }*/
  }
}
