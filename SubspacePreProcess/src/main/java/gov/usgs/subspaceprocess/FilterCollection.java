/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.subspaceprocess;
 
import com.oregondsp.signalProcessing.filter.iir.Butterworth;
import com.oregondsp.signalProcessing.filter.iir.PassbandType;

/** This class contains up to 3 channels of filters.  In SubspacePreprocess it is just used
 * to pass the filtering parameters around (hpc, lpc, npoles, type).  In the actual SubpaceDectecor
 * real time portion this is used to do the filtering as well.
 *
 * @author benz ketchum
 */
public final class FilterCollection {

  private float hpcorner;
  private float lpcorner;
  private int npoles;
  private int rate;

  private String filtertype = "bandpass";

  private Butterworth f0;
  private Butterworth f1;
  private Butterworth f2;

  public void setHPcorner(float d) {
    hpcorner = d;
  }

  public void setLPcorner(float d) {
    lpcorner = d;
  }

  public void setNpoles(int d) {
    npoles = d;
  }

  public void setRate(int d) {
    rate = d;
  }

  public void setFilterType(String d) {
    filtertype = d;
  }

  public float getHPcorner() {
    return hpcorner;
  }

  public float getLPcorner() {
    return lpcorner;
  }

  public int getNpoles() {
    return npoles;
  }

  public int getRate() {
    return rate;
  }

  public String getFilterType() {
    return filtertype;
  }

  public FilterCollection() {
  }

  public FilterCollection(float hp, float lp, int np, int r, String type, int nch) {
    setup(hp, lp, np, r, type, nch);
  }

  public FilterCollection(SubspaceTemplatesCollection t) {

    setup(t.getHPCorner(), t.getLPCorner(), t.getNpoles(), t.getRate(), t.getFilterType(), t.getNumberOfChannels());

  }

  public void initialize() {
    if (f0 != null) {
      f0.initialize();
    }
    if (f1 != null) {
      f1.initialize();
    }
    if (f2 != null) {
      f2.initialize();
    }
  }

  public void setup(float hp, float lp, int np, int r, String type, int nch) {

    hpcorner = hp;
    lpcorner = lp;
    npoles = np;
    rate = r;
    filtertype = "" + type;

    if (null != filtertype) {
      switch (filtertype) {
        case "highpass":
          f0 = new Butterworth(npoles, PassbandType.HIGHPASS,
                  hpcorner, lpcorner, 1.0 / (float) rate);
          if (nch >= 2) {
            f1 = new Butterworth(npoles, PassbandType.HIGHPASS,
                    hpcorner, lpcorner, 1.0 / (float) rate);
          }
          if (nch >= 3) {
            f2 = new Butterworth(npoles, PassbandType.HIGHPASS,
                    hpcorner, lpcorner, 1.0 / (float) rate);
          }
          break;
        case "lowpass":

          f0 = new Butterworth(npoles, PassbandType.LOWPASS,
                  hpcorner, lpcorner, 1.0 / (float) rate);
          if (nch >= 2) {
            f1 = new Butterworth(npoles, PassbandType.LOWPASS,
                    hpcorner, lpcorner, 1.0 / (float) rate);
          }
          if (nch >= 3) {
            f2 = new Butterworth(npoles, PassbandType.LOWPASS,
                    hpcorner, lpcorner, 1.0 / (float) rate);
          }
          break;
        case "bandpass":

          f0 = new Butterworth(npoles, PassbandType.BANDPASS,
                  hpcorner, lpcorner, 1.0 / (float) rate);
          if (nch >= 2) {
            f1 = new Butterworth(npoles, PassbandType.BANDPASS,
                    hpcorner, lpcorner, 1.0 / (float) rate);
          }
          if (nch >= 3) {
            f2 = new Butterworth(npoles, PassbandType.BANDPASS,
                    hpcorner, lpcorner, 1.0 / (float) rate);
          }
          break;
        default:
          break;
      }
    }
  }
  
  public void filterData(float[] data, int channel) {
    if (channel == 0) {
      f0.filter(data);
    }
    if (channel == 1) {
      f1.filter(data);
    }
    if (channel == 2) {
      f2.filter(data);
    }
  }
}
