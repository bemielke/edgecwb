/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.isi;

/**
 * This is the interface for the ISI call backs.
 *
 * @author davidketchum
 */
public interface ISICallBack {

  public void isiMiniSeed(byte[] buf, int sig, long seq);

  public void isiConnection();

  public void isiAlert(int code, String msg);

  public void isiHeartbeat();

  public void isiInitialSequence(int signature, long sequence);

  public void isiIDA10(byte[] bb, int sig, long seq, int len);

}
