/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.util.Util;

/**
 * This thread using ping to the Hughes NOCs to test that the VPNs are up.
 *
 * @author davidketchum
 */
public final class HughesNOCChecker extends Thread {

  private final PingListener nlv;
  private final PingListener gtn;
  private boolean terminate;

  public void terminate() {
    terminate = true;
  }

  @Override
  public String toString() {
    return nlv.toString() + " " + gtn.toString();
  }

  public HughesNOCChecker() {
    gtn = new PingListener("192.21.10.254", "X-GTN");
    nlv = new PingListener("192.10.21.254", "X-NLV");
    Util.prta("HughesNOCChecker : starting");
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    try {
      sleep(60000);
    } catch (InterruptedException expected) {
    }
    while (!terminate) {
      for (int i = 0; i < 120; i++) {
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        if (terminate) {
          break;
        }
      }
      Util.prta("HughesNOCChecker : " + toString());
      if (System.currentTimeMillis() - nlv.getLastRcvMillis() > 900000) {
        SendEvent.edgeSMEEvent("NLV-BHDown", "NLV has not returned a ping for " + (System.currentTimeMillis() - nlv.getLastRcvMillis()) / 1000 + " secs", this);
      }
      if (System.currentTimeMillis() - gtn.getLastRcvMillis() > 900000) {
        SendEvent.edgeSMEEvent("GTN-BHDown", "GTN has not returned a ping for " + (System.currentTimeMillis() - gtn.getLastRcvMillis()) / 1000 + " secs", this);
      }
    }
    Util.prt("HughesNOCChecker is exitting");
  }
}
