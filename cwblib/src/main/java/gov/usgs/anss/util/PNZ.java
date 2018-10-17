/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.util.ArrayList;
import java.io.*;
import java.text.DecimalFormat;

/**
 * This class is used to represent a Poles and Zeros as commonly used in seismology. When used with
 * RESP files for SEED usage a0 is normally that defined in the SEED manual. WHen this is used to
 * represent a "cooked" response it is the gain value (sensitivity) in units like counts/nm.
 *
 * @author davidketchum
 */
public final class PNZ {

  private int npoles;
  private int nzeros;
  private boolean mixedPoles;
  private double a0, a0freq, gainfreq;
  private double sensitivity = 1;
  private String originalDomain = "A";
  private ArrayList<Complex> poles, zeros;
  private static DecimalFormat df6 = new DecimalFormat("0.0000E00");
  private static DecimalFormat df5 = new DecimalFormat("0.00000");
  private static DecimalFormat df6a = new DecimalFormat("0.000000");
  private static DecimalFormat df1 = new DecimalFormat("0.0");
  private String units;
  private String resolvedUnits;

  public String getOriginalDomain() {
    return originalDomain;
  }

  public void clear() {
    npoles = 0;
    nzeros = 0;
    mixedPoles = false;
    a0 = 0;
    a0freq = 0;
    gainfreq = 0;
    poles.clear();
    zeros.clear();
    sensitivity = 1.;
  }

  public String getPNZFrequencySummary() {
    StringBuilder sb = new StringBuilder(1000);
    double pi2 = 6.283185307;
    sb.append("Npoles=").append(npoles).append(" (Hz) ");
    for (int i = 0; i < npoles; i++) {
      sb.append(i).append("@").append(df5.format(getPole(i).getModulus() / pi2)).append(" ");
    }
    sb.append("\nNzeros=").append(nzeros).append(" (Hz) ");
    for (int i = 0; i < nzeros; i++) {
      sb.append(i).append("@").append(df5.format(getZero(i).getModulus() / pi2)).append(" ");
    }
    return sb.toString();
  }

  /**
   * set the sensitivity
   *
   * @param f The A0 frequency to set
   */
  public void setA0Frequency(double f) {
    a0freq = f;
  }

  /**
   * set the sensitivity
   *
   * @param sens The sensitivity portion to set
   */
  public void setSensitivity(double sens) {
    sensitivity = sens;
  }

  /**
   * get the current sensitivity
   *
   * @return The current setting of the sensitivity
   */
  public double getSensitivity() {
    return sensitivity;
  }

  /**
   * return a string with the units from the seed volume. The encoding is first character
   * 'A'cceleration, 'V'elocity, 'D' displacement, 'U'ndefined. The second character if present
   * gives the units of distance. No 2nd character is Meters, 'n' is nanometers iF the units are not
   * recognized
   *
   * @return The SEED unit
   */
  public String getResolvedUnits() {
    return resolvedUnits;
  }

  /**
   * return a string with the units from the seed volume
   *
   * @return The SEED unit
   */
  public String getUnits() {
    return units;
  }

  public boolean areSame(PNZ pz) {
    boolean ret = true;
    if (df6 == null) {
      df6 = new DecimalFormat("0.0000E00");
    }
    if (poles != null) {
      sort(poles);
    }
    if (zeros != null) {
      sort(zeros);
    }
    if (pz.poles != null) {
      sort(pz.poles);
    }
    if (pz.zeros != null) {
      sort(pz.zeros);
    }
    if (Math.abs(1. - Math.abs(getConstant("nm") / pz.getConstant("nm"))) > 0.02) {    // Allow 2 % variation before declaring a difference
      Util.prt("Constants do not agree " + a0 + " to " + pz.getA0() + " rat=" + (pz.getA0() != 0. ? df6.format(a0 / pz.getA0()) : "***")
              + " sens =" + sensitivity + " " + pz.getSensitivity()
              + " recalc a0=" + df6.format(getA0(0.1)) + " " + df6.format(pz.getA0(0.1)) + " rat=" + df6.format(getA0(0.1) / pz.getA0(0.1)));
      ret = false;
    }
    if (npoles != pz.getNpoles()) {
      Util.prt("Number of poles do not agree " + npoles + " to " + pz.getNpoles());
      ret = false;
    }
    if (nzeros != pz.getNzeros()) {
      Util.prt("Number of zeros do not agree " + nzeros + " to " + pz.getNzeros());
      ret = false;
    }
    for (int i = 0; i < Math.min(npoles, pz.npoles); i++) {
      if (Math.abs(1. - Math.abs(poles.get(i).getReal() / pz.getPole(i).getReal())) > 0.0001) {
        Util.prt("real part of  pole[" + i + "] does not agree (" + poles.get(i).toString() + ")!=(" + pz.poles.get(i).toString() + ")");
        ret = false;
      }
      if (Math.abs(1. - Math.abs(poles.get(i).getImag() / pz.getPole(i).getImag())) > 0.0001) {
        Util.prt("Imag part of  pole[" + i + "] does not agree (" + poles.get(i).toString() + ")!=(" + pz.poles.get(i).toString() + ")");
        ret = false;
      }
    }
    for (int i = 0; i < Math.min(nzeros, pz.nzeros); i++) {
      if (Math.abs(1. - Math.abs(zeros.get(i).getReal() / pz.getZero(i).getReal())) > 0.0001) {
        Util.prt("real part of zero[" + i + "] does not agree (" + zeros.get(i).toString() + ")!=(" + pz.zeros.get(i).toString() + ")");
        ret = false;
      }
      if (Math.abs(1. - Math.abs(zeros.get(i).getImag() / pz.getZero(i).getImag())) > 0.0001) {
        Util.prt("Imag part of zero[" + i + "] does not agree (" + zeros.get(i).toString() + ")!=(" + pz.zeros.get(i).toString() + ")");
        ret = false;
      }
    }
    return ret;
  }

  private void sort(ArrayList<Complex> c) {
    for (int i = 0; i < c.size() - 1; i++) {
      for (int j = i + 1; j < c.size(); j++) {
        boolean flip = false;
        if (c.get(i).getReal() > c.get(j).getReal()) {
          flip = true;
        } else if (c.get(i).getReal() == c.get(j).getReal()) {
          if (c.get(i).getImag() > c.get(j).getImag()) {
            flip = true;
          }
        }
        if (flip) {
          Complex tmp = c.get(i);
          c.set(i, c.get(j));
          c.set(j, tmp);
        }
      }
    }
  }

  /**
   * multiple PNZ can be combined by adding the zeros and poles up and adjusting the a0
   *
   * @param pz the PNZ to combine with this one
   */
  public void combinePNZ(PNZ pz) {
    for (int i = 0; i < pz.getNpoles(); i++) {
      poles.add(pz.getPole(i));
      npoles++;
    }
    for (int i = 0; i < pz.getNzeros(); i++) {
      zeros.add(pz.getZero(i));
      nzeros++;
    }
    a0 = a0 * pz.getA0();
  }

  /**
   * The B type response is just the formulation where t(f) = A0*sens*(prod(f-zeros))/prod(f-poles).
   * We need to convert to the formulation for a A type by multiplying each pole and zero by 2Pi and
   * adjusting the constant by a compensating amount.
   */
  public final void convertFromBtoA() {
    Complex twopi = new Complex(2. * Math.PI, 0.);
    double pi2 = 2. * Math.PI;

    for (int i = 0; i < npoles; i++) {
      poles.set(i, Complex.times(twopi, poles.get(i)));
      a0 = a0 * pi2;
    }
    for (int i = 0; i < nzeros; i++) {
      zeros.set(i, Complex.times(twopi, zeros.get(i)));
      a0 = a0 / pi2;
    }

  }

  /**
   * /** add the given zero to the list
   *
   * @param real The real part
   * @param imag the imaginary part
   */
  public void addZero(double real, double imag) {
    zeros.add(new Complex(real, imag));
    nzeros++;
  }

  /**
   * add the given pole to the list
   *
   * @param real The real part
   * @param imag the imaginary part
   */
  public void addPole(double real, double imag) {
    poles.add(new Complex(real, imag));
    npoles++;
  }

  /**
   * set the a0 to a new value (a0 is really along with sensitivity gives the constant)
   *
   * @param a0in The new A0
   */
  public void setA0(double a0in) {
    a0 = a0in;
  }

  /**
   * return frequency at which a0 is calculated
   *
   * @return The frequency in Hz at which a0 is calculated.
   */
  public double getA0Freq() {
    return a0freq;
  }

  /**
   * get the frequency in Hz of the SEED gain.
   *
   * @return the gain frequency
   */
  public double getGainFreq() {
    return gainfreq;
  }

  /**
   * get the value of a0 currently in this PNZ (might be gain or sens)
   *
   * @return the a0 value
   */
  public double getA0() {
    return a0;
  }

  /**
   * string representation
   *
   * @return String with npoles, nzeros and a0
   */
  @Override
  public String toString() {
    return "Npoles=" + npoles + " Nzeros=" + nzeros + " a0=" + a0 + " sens=" + sensitivity + " unit=" + resolvedUnits;
  }

  /**
   * get the current value the constant
   *
   * @param unit The nunit as nm or um
   * @return The value of the constant at this time
   *
   */
  public double getConstant(String unit) {
    double factor = 1.e9;             // default is convert meters to nanometers.
    if (resolvedUnits.length() >= 2) {
      if (resolvedUnits.substring(1, 2).equalsIgnoreCase("n")) {
        factor = 1.; // already in nanometers
      }
    }
    double scale = 1.;
    if (unit.equalsIgnoreCase("um")) {
      scale = 1000.;
    }
    return a0 * sensitivity / factor * scale;
  }

  /**
   * return the response formatted as a SAC string
   *
   * @param unit The unit desired nm or um
   * @return The PNZ as a SAC string
   */
  public final String toSACString(String unit) {
    int nzerosToAdd = -1;
    if (resolvedUnits.substring(0, 1).equals("A")) {
      nzerosToAdd = 2;
    }
    if (resolvedUnits.substring(0, 1).equals("V")) {
      nzerosToAdd = 1;
    }
    if (resolvedUnits.substring(0, 1).equals("D")) {
      nzerosToAdd = 0;
    }
    if (resolvedUnits.substring(0, 1).equals("P")) {
      nzerosToAdd = 0;
    }
    if (resolvedUnits.substring(0, 1).equals("-")) {
      Util.prta(" ***** PNZ attempt to SAC output a pass through analog stage!");
    }

    double factor = 1.e9;             // default is convert meters to nanometers.
    if (resolvedUnits.length() >= 2) {
      if (resolvedUnits.substring(1, 2).equalsIgnoreCase("n")) {
        factor = 1.; // already in nanometers
      }
    }
    if (nzerosToAdd == -1) {
      Util.prt(" ***** Units not known in toSacString!!!! units=" + units + " resolved=" + resolvedUnits);
      return "CONSTANT             1.0000E+00\nZEROS   0\nPOLES   0\n";
    }
    StringBuilder sb = new StringBuilder(200);
    if (df6 == null) {
      df6 = new DecimalFormat("0.0000E00");
    }
    double scale = 1.;
    if (unit.equalsIgnoreCase("um")) {
      scale = 1000.;
    } else if (unit.equalsIgnoreCase("pa")) {
      scale = 1.;
    } else if (!unit.equalsIgnoreCase("nm")) {
      Util.prt("****** Illegal units for PNZ conversion to SAC=" + unit);
    }
    sb.append("CONSTANT             ").append(a0 > 0 ? " " : "").append(fix(df6.format(a0 * sensitivity / factor * scale))).append("\n");
    sb.append("ZEROS").append(Util.leftPad("" + (nzeros + nzerosToAdd), 4)).append("\n");
    String z = fix(df6.format(0.));
    for (int i = 0; i < nzerosToAdd; i++) {
      sb.append("         ").append(z).append("   ").append(z).append("\n");
    }
    for (int i = 0; i < nzeros; i++) {
      sb.append("        ").append(zeros.get(i).getReal() >= 0. ? " " : "").append(fix(df6.format(zeros.get(i).getReal()))).append("  ").append(zeros.get(i).getImag() >= 0. ? " " : "").append(fix(df6.format(zeros.get(i).getImag()))).append("\n");
    }
    sb.append("POLES").append(Util.leftPad("" + npoles, 4)).append("\n");
    for (int i = 0; i < npoles; i++) {
      sb.append("        ").append(poles.get(i).getReal() >= 0. ? " " : "").append(fix(df6.format(poles.get(i).getReal()))).append("  ").append(poles.get(i).getImag() >= 0. ? " " : "").append(fix(df6.format(poles.get(i).getImag()))).append("\n");
    }
    sb.append("\n");
    return sb.toString();
  }

  public String toStaXmlString(String sp) {
    StringBuilder sb = new StringBuilder(1000);
    sb.append(sp).append("<PNZ>\n").append(sp).append("  <InputUnits>").append(units).append("</InputUnits>\n").append(sp).append("  <OutputUnits>VOLTS</OutputUnits>\n").append(sp).append("  <NormalizationFactor>").append(df6.format(a0)).append("</NormalizationFactor>\n").append(sp).append("  <NormalizationFreq>").append(a0freq).append("</NormalizationFreq>\n");
    for (int i = 0; i < npoles; i++) {
      sb.append(sp).append("  <Pole number=\"").append(i).append("\">\n").append(sp).append("    <Real>").append(df6.format(poles.get(i).getReal())).append("</Real>\n").append(sp).append("    <Imaginary>").append(df6.format(poles.get(i).getImag())).append("</Imaginary>\n").append(sp).append("  </Pole>\n");
    }
    for (int i = 0; i < nzeros; i++) {
      sb.append(sp).append("  <Zero number=\"").append(i).append("\">\n").append(sp).append("    <Real>").append(df6.format(zeros.get(i).getReal())).append("</Real>\n").append(sp).append("    <Imaginary>").append(df6.format(zeros.get(i).getImag())).append("</Imaginary>\n").append(sp).append("  </Zero>\n");
    }
    sb.append(sp).append("</PNZ>\n");
    return sb.toString();
  }

  private String fix(String s) {
    int pos;
    if ((pos = s.indexOf("E")) > 0) {
      if (Character.isDigit(s.charAt(pos + 1))) {
        return s.substring(0, pos + 1) + "+" + s.substring(pos + 1);
      }
    }
    return s;
  }

  /**
   * create an empty PNZ - no poles are zeros and a0, sensitivity of 1, units are unknown. Normally
   * there should be a call to setUnits() to set the units and resolved units.
   */
  public PNZ() {
    poles = new ArrayList<>(npoles);
    zeros = new ArrayList<>(nzeros);
    npoles = 0;
    nzeros = 0;
    a0 = 1;
    resolvedUnits = "U";
    units = "??";
    sensitivity = 1.;
    a0freq = 1.;

  }

  public void setUnits(String un) {
    units = un;
    resolvedUnits = chkUnits(units);
  }

  /**
   * Creates a new instance of PNZ from text in resp form or text from stasrv form
   *
   * @param pnztext Text for PNZ section in RESP or in stasrv form
   */
  public PNZ(String pnztext) {
    String line;
    // If B053F07 is not present, this must be a cooked response from StaSrv
    if (!pnztext.contains("B053F07") && !pnztext.contains("B043F08")) {
      try {
        nzeros = -1;
        npoles = -1;
        a0 = 1.;
        sensitivity = 1;
        BufferedReader in = new BufferedReader(new StringReader(pnztext));
        units = "NM";
        resolvedUnits = "Dn";
        while ((line = in.readLine()) != null) {
          if (line.contains("CONST")) {
            String[] split = line.split("T");
            split[2] = split[2].trim();
            a0 = Double.parseDouble(split[2]);
            break;
          }
        }
        if (line == null) {
          Util.prt("PNZ constructor bad SAC input=" + pnztext);
          return;
        }     // this did not decode
        line = in.readLine();
        if (line.contains("ZEROS")) {
          String[] split = line.split("S");
          split[1] = split[1].trim();
          nzeros = Integer.parseInt(split[1]);
          zeros = new ArrayList<>(nzeros);
          for (int i = 0; i < nzeros; i++) {
            line = in.readLine().trim();
            for (int j = 0; j < 5; j++) {
              line = line.replaceAll("  ", " ");
            }
            split = line.split("\\s");
            zeros.add(new Complex(Double.parseDouble(split[0]), Double.parseDouble(split[1])));
          }
        } else {
          return;
        }
        line = in.readLine();
        if (line.contains("POLES")) {
          String[] split = line.split("S");
          split[1] = split[1].trim();
          npoles = Integer.parseInt(split[1]);
          poles = new ArrayList<>(npoles);
          for (int i = 0; i < npoles; i++) {
            line = in.readLine().trim();
            for (int j = 0; j < 5; j++) {
              line = line.replaceAll("  ", " ");
            }
            split = line.split("\\s");
            poles.add(new Complex(Double.parseDouble(split[0]), Double.parseDouble(split[1])));
          }
        } else {
          return;
        }
        return;
      } catch (IOException e) {
        Util.prt("IOException parsing PNZ");
      } catch (NumberFormatException e) {
        Util.prt("Number format exception");
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
    }

    // It must be a RESP file to have gotten here.
    try {
      BufferedReader in = new BufferedReader(new StringReader(pnztext));
      units = getValue(pnztext, "B053F05|B043F06");   // Get engineering Units from B53
      resolvedUnits = chkUnits(units);
      a0 = Double.parseDouble(getValue(pnztext, "B053F07|B043F08"));
      a0freq = Double.parseDouble(getValue(pnztext, "B053F08|B043F09"));
      npoles = Integer.parseInt(getValue(pnztext, "B053F14|B043F15"));
      nzeros = Integer.parseInt(getValue(pnztext, "B053F09|B043F10"));
      String responseType = getValue(pnztext, "B053F03|B043F05");
      if (!(responseType.substring(0, 1).equals("A") || responseType.substring(0, 1).equals("B"))) {
        Util.prt("***** PNZ: Unhandled response type=" + responseType + " making unity gain flat stage.");
        npoles = 0;
        nzeros = 0;
        a0 = 1.;
        sensitivity = 1.;
        return;
      }

      String fstr = getValue(pnztext, "B058F05|B043F09");
      if (fstr.indexOf(" Hz") > 0 || fstr.indexOf(" HZ") > 0) {
        fstr = fstr.substring(0, fstr.length() - 3);
      }

      gainfreq = Double.parseDouble(fstr);
      poles = new ArrayList<>(npoles);
      zeros = new ArrayList<>(nzeros);
      int countz = 0;
      int countp = 0;
      for (;;) {
        line = in.readLine();
        if (line == null) {
          break;
        }
        if (line.length() < 7) {
          continue;
        }
        String type = line.substring(0, 7);

        if (type.equals("B053F10") || type.equals("B053F15") || type.equals("B043F11") || type.equals("B043F16")) {
          line = line.replaceAll("      ", " ");
          line = line.replaceAll("     ", " ");
          line = line.replaceAll("    ", " ");
          line = line.replaceAll("   ", " ");
          line = line.replaceAll("  ", " ");

          String[] parts = line.split(" ");
          int index = Integer.parseInt(parts[1]);
          double real = Double.parseDouble(parts[2]);
          double imag = Double.parseDouble(parts[3]);
          if (type.equals("B053F10") || type.equals("B043F11")) {
            zeros.add(index, new Complex(real, imag));
            countz++;
            //Util.prt("adding zero "+index+"="+real+"+ i*"+imag);
          }
          if (type.equals("B053F15") || type.equals("B043F16")) {
            poles.add(index, new Complex(real, imag));
            countp++;
            //Util.prt("Adding pole "+index+"="+real+"i*"+imag);

          }
        }
      }

      // IF this is a B type response, we need to convert to Radians from frequency
      if (responseType.substring(0, 1).equals("B")) {
        convertFromBtoA();
      }
      double calca0 = getA0(a0freq);

      // If this RESP file uses poles with positive real parts for all of its poles, log it and reverse the signs
      if (npoles > 0) {
        boolean allpos = true;
        boolean allneg = true;
        for (int i = 0; i < npoles; i++) {
          if (poles.get(i).getReal() < 0.) {
            allpos = false;
          }
          if (poles.get(i).getReal() > 0.) {
            allneg = false;
          }
        }
        if (allpos) {
          Util.prt("     #### positive real parts on all Poles - reverse sign" + toSACString("um"));
          Complex minus1 = new Complex(-1., 0.);
          for (int i = 0; i < npoles; i++) {
            poles.set(i, Complex.times(minus1, poles.get(i)));
          }
        }
        if (!allpos && !allneg) {
          Util.prt("     **** mixed real parts on poles=" + toSACString("um"));
          mixedPoles = true;
        }

      }
    } catch (IOException e) {
      Util.prt("IOException parsing PNZ");
    } catch (NumberFormatException e) {
      Util.prt("Number format exception");
      e.printStackTrace();
    }
  }

  private String chkUnits(String in) {
    // Sometimes the units line contains the # of the dictionary at the beginning, if so strip it off
    while (Character.isDigit(in.charAt(0)) || in.charAt(0) == ' ') {
      in = in.substring(1);
    }
    in = in.trim() + "   ";
    try {
      if (in.indexOf("NM/S ") == 0) {
        return "Vn";
      } else if (in.indexOf("M/S ") == 0) {
        return "V";
      } else if (in.indexOf("NM/S**2 ") == 0) {
        return "An";
      } else if (in.indexOf("M/S**2 ") == 0) {
        return "A";
      } else if (in.indexOf("M/S/S ") == 0) {
        return "A";
      } else if (in.indexOf("NM/S/S ") == 0) {
        return "An";
      } else if (in.indexOf("NM") == 0) {
        return "Dn";
      } else if (in.substring(0, 3).equalsIgnoreCase("M -")) {
        return "D";
      } else if (in.indexOf("M  ") == 0) {
        return "D";
      } else if (in.indexOf("V -") == 0) {
        return "-";     // This -is a pure analog stage, normally get combined with another 53
      } else if (in.indexOf("VOLTS -") == 0 || in.contains("Volts")) {
        return "-";     // This -is a pure analog stage, normally get combined with another 53
      } else if (in.indexOf("PA") == 0) {
        return "Pn";     // This -is a pressure unit, do not disturb it
      } else {
        Util.prta(" ****** Unknown Unit in PNZ - cannot convert units=" + in + "|");
        return "U";
      }
    }
    catch(RuntimeException e) {
      Util.prta("**** PNZ.chkUnits() bad input ="+in+" threw exception ="+e);
      throw e;
    }  
  }

  /**
   * return whether mixed poles were found in this PNZ
   *
   * @return true if mixed poles are found
   */
  public boolean isMixedPoles() {
    return mixedPoles;
  }

  /**
   * return the response value as a complex. A0 is the scale factor to multiply the ratio of the
   * products of the zeros/poles.
   *
   * @param f The frequency in Hz at which the response is desired.
   * @return The complex response a0* prod(s-zeros)/prod(s-poles)
   */
  public Complex getResponse(double f) {
    Complex num = new Complex(a0, 0.);
    Complex denom = new Complex(1., 0.);
    Complex s = new Complex(0., 2. * Math.PI * f);
    for (int i = 0; i < nzeros; i++) {
      num = Complex.times(num, Complex.subtract(s, (Complex) zeros.get(i)));
    }
    for (int i = 0; i < npoles; i++) {
      denom = Complex.times(denom, Complex.subtract(s, (Complex) poles.get(i)));
    }
    return Complex.divide(num, denom);
  }

  /**
   * return the amplitude portion of the response at given frequency
   *
   * @param f The frequency in Hz at which the response is desired.
   * @return The complex response modulus(a0* prod(s-zeros)/prod(s-poles))
   */
  public double getAmp(double f) {
    return getResponse(f).getModulus();
  }

  /**
   * return the phase portion of the response at given frequency in degrees
   *
   * @param f The frequency in Hz at which the response is desired.
   * @return The complex response atax2(imag(resp)/real(resp)) converted to degrees
   */
  public double getPhase(double f) {
    Complex ans = getResponse(f);
    return Math.atan2(ans.getImag(), ans.getReal()) * 180. / Math.PI;

  }

  /**
   * Calculate the value of a0 as presented by SEED from the poles and zeros. That is
   * 1./modulus(prod(s-zeros)/prod(s-poles))
   *
   * @param f The frequency in Hz at which the response is desired.
   * @return a0 value per seed =1./modulus(a0* prod(s-zeros)/prod(s-poles))
   */
  public final double getA0(double f) {
    Complex num = new Complex(1., 0.);
    Complex denom = new Complex(1., 0.);
    Complex s = new Complex(0., 2. * Math.PI * f);
    for (int i = 0; i < nzeros; i++) {
      num = Complex.times(num, Complex.subtract(s, (Complex) zeros.get(i)));
    }
    for (int i = 0; i < npoles; i++) {
      denom = Complex.times(denom, Complex.subtract(s, (Complex) poles.get(i)));
    }
    //Util.prt("complex ans="+Complex.divide(num,denom));
    return 1. / Complex.divide(num, denom).getModulus();
  }

  /**
   * Calculate the value phase in degrees as presented by SEED from the poles and zeros. That is
   * ans=(prod(s-zeros)/prod(s-poles)), return is atan2(imag(ans), real(ans) in degrees.
   *
   * @param f The frequency in Hz at which the response is desired.
   * @return phase(a0) value per seed atan2(imag(prod(s-zeros)/prod(s-poles),real(...)))
   */
  public double getA0Phase(double f) {
    Complex num = new Complex(1., 0.);
    Complex denom = new Complex(1., 0.);
    Complex s = new Complex(0., 2. * Math.PI * f);
    for (int i = 0; i < nzeros; i++) {
      num = Complex.times(num, Complex.subtract(s, (Complex) zeros.get(i)));
    }
    for (int i = 0; i < npoles; i++) {
      denom = Complex.times(denom, Complex.subtract(s, (Complex) poles.get(i)));
    }
    //Util.prt("complex ans="+Complex.divide(num,denom));
    Complex ans = Complex.divide(num, denom);
    double phase = Math.atan2(ans.getImag(), ans.getReal()) * 180. / Math.PI;
    return phase;
  }

  /**
   * get the number of poles
   *
   * @return The number of poles
   */
  public int getNpoles() {
    return npoles;
  }

  /**
   * get the number of zeros
   *
   * @return The number of zeros
   */
  public int getNzeros() {
    return nzeros;
  }

  /**
   * get the ith pole
   *
   * @param i The number of the pole to get
   * @return The pole
   */
  public Complex getPole(int i) {
    return poles.get(i);
  }

  /**
   * get the ith zero
   *
   * @param i The number of the zero to get
   * @return The zero
   */
  public Complex getZero(int i) {
    return zeros.get(i);
  }

  /**
   * for a resp formated string in sb, find the key and return its value
   *
   * @param sb A resp formatted string
   * @param keyin The B???F??? of the value desired.
   * @return The value of the field
   */
  public static String getValue(String sb, String keyin) {
    String[] keys = keyin.split("\\|");
    for (String key : keys) {
      int i = sb.indexOf(key);
      int end = sb.indexOf("\n", i);
      if (i < 0 || end < 0) {
        continue;
      }
      //Util.prt("get Value key="+key+" beg="+i+" end="+end+" line="+sb.substring(i,end));
      int beg = sb.indexOf(":", i);
      String val = sb.substring(beg + 1, end).trim();
      return val;
    }

    //Util.prt("value="+val+"|");
    return "";
  }

  /**
   * test main
   *
   * @param args - not used
   */
  public static void main(String[] args) {
    double nomf = -1.;
    int ndec = 10;
    PNZ pnz = null;
    String calibFile = null;
    ArrayList<String> calibLines = null;
    String line;
    String s1 = "";
    String s2 = "";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-@")) {
        nomf = Double.parseDouble(args[i + 1]);
      }
      if (args[i].equals("-compare")) {
        try {
          BufferedReader in = new BufferedReader(new FileReader(args[i + 1]));
          while ((line = in.readLine()) != null) {
            s1 += line + "\n";
          }
          in.close();
          in = new BufferedReader(new FileReader(args[i + 2]));
          while ((line = in.readLine()) != null) {
            s2 += line + "\n";
          }
          in.close();
        } catch (IOException e) {
          Util.prt("Read error on file e=" + e);
          System.exit(0);
        }
        PNZ p1 = new PNZ(s1);
        PNZ p2 = new PNZ(s2);
        double logf = -4.;
        double loginc = 0.05;
        double f;
        double amp1;
        double amp2;
        double ph1;
        double ph2;
        double pct;
        double maxph = 0;
        double maxamp = 0;
        double fmaxph = 0;
        double fmaxamp = 0;
        while (logf < 2.) {
          f = Math.pow(10., logf);
          Complex resp1 = p1.getResponse(f);
          Complex resp2 = p2.getResponse(f);
          amp1 = p1.getAmp(f);
          ph1 = p1.getPhase(f);
          amp2 = p2.getAmp(f);
          ph2 = p2.getPhase(f);
          pct = (amp1 / amp2 - 1.) * 100;
          if (Math.abs(pct) > maxamp) {
            maxamp = Math.abs(pct);
            fmaxamp = f;
          }
          if (Math.abs(ph1 - ph2) > maxph) {
            maxph = Math.abs(ph1 - ph2);
            fmaxph = f;
          }
          //if(Math.abs(pct) > 2. || Math.abs(ph1 - ph2) > 2)
          Util.prt(df5.format(f) + " " + df5.format(pct) + " " + df5.format(ph1 - ph2) + (Math.abs(pct) > 1. || Math.abs(ph1 - ph2) > 1 ? "*" : ""));
          logf += loginc;

        }
        Util.prt("max amp=" + df1.format(maxamp) + "% at " + df5.format(fmaxamp) + " max phase=" + df1.format(maxph) + " at " + df5.format(fmaxph));
        System.exit(0);
      }
      if (args[i].equals("-calib")) {
        calibFile = args[i + 1];
        calibLines = new ArrayList<>(1000);
        try {
          BufferedReader in = new BufferedReader(new FileReader(calibFile));
          while ((line = in.readLine()) != null) {
            if (line.indexOf("current") >= 0.) {
              String[] parts = line.split("\\s");
              //Util.prt(parts.length+" "+line);
              if (parts.length >= 7) {
                calibLines.add((parts[1] + "   ").substring(0, 5) + parts[2].substring(0, 3) + " " + parts[5] + " " + parts[6]);
              }
            }
          }
        } catch (IOException e) {
          Util.prt("Error reading calib file! e=" + e);
          e.printStackTrace();
          System.exit(101);
        }
      }
      if (args[i].equals("-f")) {
        Util.prt("        Filename         f(Hz)    PNZAmp    PNZPhase    1/PNZAmp   CalibAmp  %diff  Calper");
        for (int jfile = i + 1; jfile < args.length; jfile++) {
          try {
            byte[] b;
            try (RandomAccessFile rw = new RandomAccessFile(args[jfile].trim(), "rw")) {
              b = new byte[(int) rw.length()];
              rw.read(b, 0, (int) rw.length());
            }
            String s = new String(b);
            pnz = new PNZ(s);

          } catch (IOException e) {
            e.printStackTrace();
          }
          double base = -3.;
          double f;
          if (pnz != null) {
            double calib = -1.;
            double calper = -1.;
            if (calibFile != null) {
              for (String calibLine : calibLines) {
                if (calibLine.substring(0, 8).equals(args[jfile].trim().substring(2, 10).replaceAll("_", " "))) {
                  String[] parts = calibLine.substring(9).split("\\s");
                  if (parts.length >= 2) {
                    calib = Double.parseDouble(parts[0]);
                    calper = Double.parseDouble(parts[1]);
                    break;
                  }
                }
              }
            }
            if (nomf > 0. || calper > 0.) {
              f = nomf;
              if (calper > 0) {
                f = 1. / calper;
              }
              double pct = (1. / pnz.getAmp(f) - calib) / calib * 100;
              Util.prt(args[jfile].trim() + " " + Util.leftPad(df5.format(f), 10) + " " + Util.leftPad(df5.format(pnz.getAmp(f)), 10) + " "
                      + Util.leftPad(df5.format(pnz.getPhase(f)), 10) + " 1/amp=" + df5.format(1. / pnz.getAmp(f))
                      + (calibFile == null ? "" : " " + df6a.format(calib) + " " + Util.leftPad(df1.format(pct), 6) + " " + df5.format(1. / calper))
                      + (Math.abs(pct) > 20. ? " *" : ""));
            } else {
              for (int j = 0; j < 5 * ndec + 1; j++) {
                f = Math.pow(10., base + j * 1. / (ndec));
                Util.prt(args[jfile].trim() + " " + df5.format(f) + " " + df5.format(pnz.getAmp(f)) + " "
                        + df5.format(pnz.getPhase(f)) + " 1/amp=" + df5.format(1. / pnz.getAmp(f)));
              }
            }
          }
        }
        System.exit(0);

      }
    }
    boolean saceval = false;
    String sacfile = "";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-sac")) {
        saceval = true;
        sacfile = args[i + 1];
      }
    }
    if (saceval) {
      try {
        DecimalFormat df4 = new DecimalFormat("0.0000");
        DecimalFormat dfe = new DecimalFormat("0.0000E0");
        //DecimalFormat df1 = new DecimalFormat("0.0");
        BufferedReader in = new BufferedReader(new FileReader(sacfile));
        double constant;
        double[] coord = new double[3];
        double[] orient = new double[3];
        double sensSeed = 0.;
        double sensCalc = 0.;
        double a0Seed = 0.;
        double a0Calc = 0.;
        double instrumentGain = 0.;
        String instrumentUnit, comment, instrumentType, longName;

        while ((line = in.readLine()) != null) {
          if (line.indexOf("LAT-SEED") > 0) {
            coord[0] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("LONG-SEED") > 0) {
            coord[1] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("ELEV-SEED") > 0) {
            coord[2] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("AZIMUTH") > 0) {
            orient[0] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("DIP") > 0) {
            orient[1] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("CONSTANT") == 0) {
            constant = Double.parseDouble(line.substring(15).trim());
            line = in.readLine();
            line = line.replaceAll("ZEROS", "").trim();
            int nzeros = Integer.parseInt(line);
            pnz = new PNZ();
            for (int i = 0; i < nzeros; i++) {
              line = in.readLine().trim();
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              String[] parts = line.split("\\s");
              if (parts.length == 2) {
                double real = Double.parseDouble(parts[0]);
                double imag = Double.parseDouble(parts[1]);
                pnz.addZero(real, imag);
              }
            }
            line = in.readLine();
            line = line.replaceAll("POLES", "").trim();
            int npoles = Integer.parseInt(line);
            for (int i = 0; i < npoles; i++) {
              line = in.readLine().trim();
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              String[] parts = line.split("\\s");
              if (parts.length == 2) {
                double real = Double.parseDouble(parts[0]);
                double imag = Double.parseDouble(parts[1]);
                pnz.addPole(real, imag);
              }
            }
            pnz.setA0(constant);
            double f = 0.001;
            for (int i = 0; i < 500; i++) {
              f = -3. + i * 0.01;
              f = Math.pow(10, f);
              Complex resp = pnz.getResponse(f);
              double mag = resp.getModulus();
              double phase = resp.getPhaseDegrees();
              Util.prt(df4.format(f) + " " + dfe.format(mag) + " " + df1.format(phase));
            }
          } else if (line.indexOf("SENS-SEED") > 0) {
            sensSeed = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("SENS-CALC") > 0) {
            sensCalc = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("A0-SEED") > 0) {
            a0Seed = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("A0-CALC") > 0) {
            a0Calc = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("INSTRMNTUNIT") > 0) {
            instrumentUnit = line.substring(15).trim();
          } else if (line.indexOf("INSTRMNTGAIN") > 0) {
            instrumentGain = Double.parseDouble(line.substring(15).trim());
          } else if (line.indexOf("INSTRMNTCMNT") > 0) {
            comment = line.substring(15).trim();
          } else if (line.indexOf("INSTRMNTTYPE") > 0) {
            instrumentType = line.substring(15).trim();
          } else if (line.indexOf("DESCRIPTION") > 0) {
            longName = line.substring(15).trim();
          }

        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      System.exit(0);
    }
    String in = "#               =======================================" + "\n"
            + "#" + "\n"
            + "B053F03     Transfer function type:                A [Laplace Transform (Rad/sec)]" + "\n"
            + "B053F04     Stage sequence number:                 1" + "\n"
            + "B053F05     Response in units lookup:              M/S - Velocity in Meters Per Second" + "\n"
            + "B053F06     Response out units lookup:             V - Volts" + "\n"
            + "B053F07     A0 normalization factor:               1.1179E+11" + "\n"
            + "B053F08     Normalization frequency:               1" + "\n"
            + "B053F09     Number of zeroes:                      2" + "\n"
            + "B053F14     Number of poles:                       6" + "\n"
            + "#               Complex zeroes:" + "\n"
            + "#                 i  real          imag          real_error    imag_error" + "\n"
            + "B053F10-13    0  0.000000E+00  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "B053F10-13    1  0.000000E+00  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "#               Complex poles:" + "\n"
            + "#                 i  real          imag          real_error    imag_error" + "\n"
            + "B053F15-18    0 -3.142000E-02  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "B053F15-18    1 -1.979000E-01  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "B053F15-18    2 -2.011000E+02  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "B053F15-18    3 -6.974000E+02  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "B053F15-18    4 -7.540000E+02  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "B053F15-18    5 -1.056000E+03  0.000000E+00  0.000000E+00  0.000000E+00" + "\n"
            + "#" + "\n"
            + "#               +                  +---------------------------------------+                  +" + "\n"
            + "#               +                  |       Channel Gain,  CMG3-NSN         |                  +" + "\n"
            + "#               +                  +---------------------------------------+                  +" + "\n"
            + "#" + "\n"
            + "B058F03     Stage sequence number:                 1" + "\n"
            + "B058F04     Gain:                                  0.12345E+03" + "\n"
            + "B058F05     Frequency of gain:                     1.000000E+00 HZ" + "\n"
            + "B058F06     Number of calibrations:                0" + "\n";
    PNZ p = new PNZ(in);
    Util.prt("1 Hz a0=" + p.getA0(1.) + " ph=" + p.getA0Phase(1.));
    Util.prt(".1 Hz a0=" + p.getA0(.1) + " ph=" + p.getA0Phase(.1));
    Util.prt(".05 Hz a0=" + p.getA0(.05) + " ph=" + p.getA0Phase(.05));

  }
}
