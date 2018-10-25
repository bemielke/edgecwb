
package subspacedetector;

import com.oregondsp.signalProcessing.filter.iir.Butterworth;
import com.oregondsp.signalProcessing.filter.iir.PassbandType;

/** Create an object for filtering continuous time series.
 *
 * @author benz ketchum 
 */
public final class FilterCollection {

  private float hpcorner;
  private float lpcorner;
  private int npoles;
  private int rate;

  private String filtertype = "bandpass";

  Butterworth f0;
  Butterworth f1;
  Butterworth f2;

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

  public final void setup(float hp, float lp, int np, int r, String type, int nch) {

    hpcorner = hp;
    lpcorner = lp;
    npoles = np;
    rate = r;
    filtertype = "" + type;

    if (null != filtertype) {
      switch (filtertype) {
        case "highpass":
          switch (nch) {
            case 1:
              f0 = new Butterworth(npoles, PassbandType.HIGHPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            case 2:
              f0 = new Butterworth(npoles, PassbandType.HIGHPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f1 = new Butterworth(npoles, PassbandType.HIGHPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            case 3:
              f0 = new Butterworth(npoles, PassbandType.HIGHPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f1 = new Butterworth(npoles, PassbandType.HIGHPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f2 = new Butterworth(npoles, PassbandType.HIGHPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            default:
              break;
          }
          break;
        case "lowpass":
          switch (nch) {
            case 1:
              f0 = new Butterworth(npoles, PassbandType.LOWPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            case 2:
              f0 = new Butterworth(npoles, PassbandType.LOWPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f1 = new Butterworth(npoles, PassbandType.LOWPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            case 3:
              f0 = new Butterworth(npoles, PassbandType.LOWPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f1 = new Butterworth(npoles, PassbandType.LOWPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f2 = new Butterworth(npoles, PassbandType.LOWPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            default:
              break;
          }
          break;
        case "bandpass":
          switch (nch) {
            case 1:
              f0 = new Butterworth(npoles, PassbandType.BANDPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            case 2:
              f0 = new Butterworth(npoles, PassbandType.BANDPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f1 = new Butterworth(npoles, PassbandType.BANDPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            case 3:
              f0 = new Butterworth(npoles, PassbandType.BANDPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f1 = new Butterworth(npoles, PassbandType.BANDPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              f2 = new Butterworth(npoles, PassbandType.BANDPASS,
                      hpcorner, lpcorner, 1.0 / (float) rate);
              break;
            default:
              break;
          }
          break;
        default:
          break;
      }
    }
  }

  public void filterData(float[] data, int channel) {
    switch (channel) {
      case 0:
        f0.filter(data);
        break;
      case 1:
        f1.filter(data);
        break;
      case 2:
        f2.filter(data);
        break;
      default:
        break;
    }
  }
}
