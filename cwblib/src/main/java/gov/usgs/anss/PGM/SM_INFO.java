/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.PGM;

import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.ArrayList;
import java.text.DecimalFormat;
import java.sql.ResultSet;
import java.sql.SQLException;
import gov.usgs.anss.util.Util;

/**
 * Encapsulate StrongMotion record information and allow encoding in XML for shake map.
 *
 * @author davidketchum
 */
public final class SM_INFO {

  private static final DecimalFormat df6 = new DecimalFormat("0.000000");
  public String seedname;
  public String quakeID;
  public String author;
  public double latitude;
  public double longitude;
  public String longName;
  public GregorianCalendar trigger = new GregorianCalendar();
  public GregorianCalendar altTime;
  public int altCode;
  public double pga;
  public GregorianCalendar tpga = new GregorianCalendar();
  public double pgv;
  public GregorianCalendar tpgv = new GregorianCalendar();
  public double pgd;
  public GregorianCalendar tpgd = new GregorianCalendar();
  public int nrsa;
  public double[] pdrsa;
  public double[] rsa;
  public int maxData;
  public int minData;

  public SM_INFO(String seed, String QUID, String auth, GregorianCalendar trig) {
    seedname = seed;
    quakeID = QUID;
    author = auth;
    trigger.setTimeInMillis(trig.getTimeInMillis());
    nrsa = 3;
    pdrsa = new double[nrsa];
    rsa = new double[nrsa];
  }

  public SM_INFO(String seed, String QID, String auth, GregorianCalendar trig, double pa, double pv, double pd, int nrs, double[] prs, double[] rs) {
    nrsa = nrs;
    pdrsa = new double[nrsa];
    rsa = new double[nrsa];
    seedname = seed;
    quakeID = QID;
    author = auth;
    trigger.setTimeInMillis(trig.getTimeInMillis());
    pga = pa;
    pgv = pv;
    pgd = pd;
    for (int i = 0; i < nrsa; i++) {
      pdrsa[i] = prs[i];
      rsa[i] = rs[i];
    }
  }

  public SM_INFO(ResultSet rs, char comp) throws SQLException {
    nrsa = 3;
    rsa = new double[nrsa];
    pdrsa = new double[nrsa];
    seedname = rs.getString("channel");
    tpga = new GregorianCalendar();
    tpga.setTimeInMillis(rs.getTimestamp("pgatime" + comp).getTime());
    pga = rs.getFloat("pga" + comp);
    pgv = rs.getFloat("pgv" + comp);
    pgd = rs.getFloat("pgd" + comp);
    pdrsa[0] = rs.getFloat("prsa1");
    pdrsa[1] = rs.getFloat("prsa2");
    pdrsa[2] = rs.getFloat("prsa3");
    rsa[0] = rs.getFloat("rsa1" + comp);
    rsa[1] = rs.getFloat("rsa2" + comp);
    rsa[2] = rs.getFloat("rsa3" + comp);

  }

  @Override
  public String toString() {
    return seedname + " " + latitude + " " + longitude + " " + author + "/" + quakeID + " time=" + Util.ascdate(trigger) + " " + Util.asctime2(trigger) + " " + longName
            + "\nmin=" + minData + " max=" + maxData
            + "\npga=" + df6.format(pga) + " @ " + Util.ascdate(tpga) + " " + Util.asctime2(tpga)
            + "\npgv=" + df6.format(pgv) + " @ " + Util.ascdate(tpgv) + " " + Util.asctime2(tpgv)
            + "\npgd=" + df6.format(pgd) + " @ " + Util.ascdate(tpgd) + " " + Util.asctime2(tpgd) + "\nrsa [" + pdrsa[0] + "]="
            + df6.format(rsa[0]) + " [" + df6.format(pdrsa[1]) + "]=" + df6.format(rsa[1])
            + " [" + pdrsa[2] + "]=" + df6.format(rsa[2]);
  }

  public String getShakeMapComponentXML() {
    StringBuilder sb = new StringBuilder(3000);
    sb.append("      <component name=\"").append(seedname.substring(7, 10)).append("\">\n");
    sb.append("        <acc value=\"").append(df6.format(pga)).append("\" units=\"cm/s/s\"/>\n");
    sb.append("        <vel value=\"").append(df6.format(pgv)).append("\" units=\"cm/s\"/>\n");
    for (int i = 0; i < nrsa; i++) {
      sb.append("        <sa period=\"").append(pdrsa[i]).append("\" value=\"").append(df6.format(rsa[i])).append("\" units=\"cm/s/s\"/>\n");
    }
    sb.append("      </component>\n");

    return sb.toString();
  }

  public static String getShakeMapXML(ArrayList<SM_INFO> sms) {
    StringBuilder sb = new StringBuilder(3000);
    SM_INFO sm = sms.get(0);
    sb.append("<?xml version=\"1.0\" encoding=\"US-ASCII\" standalone=\"yes\"?>\n" + "<amplitudes agency=\"NEIC\">\n"
            + "  <record>\n" + "    <timing>\n" + "      <reference zone=\"GMT\" quality=\"0.5\">\n"
            + "        <year value=\"").append(sm.trigger.get(Calendar.YEAR)).append("\"/>");
    sb.append("<month value=\"").append(sm.trigger.get(Calendar.MONTH) + 1).append("\"/>");
    sb.append("<day value=\"").append(sm.trigger.get(Calendar.DAY_OF_MONTH)).append("\"/>");
    sb.append("<hour value=\"").append(sm.trigger.get(Calendar.HOUR_OF_DAY)).append("\"/>");
    sb.append("<minute value=\"").append(sm.trigger.get(Calendar.MINUTE)).append("\"/>");
    sb.append("<second value=\"").append(sm.trigger.get(Calendar.SECOND)).append("\"/>");
    sb.append("\n      </reference>\n");
    sb.append("      <trigger value=\"0\"/>");
    sb.append("\n    </timing>\n");
    sb.append("    <station code=\"").append(sm.seedname.substring(2, 7).trim()).append("\" lat=\"").append(sm.latitude).
            append("\" lon=\"").append(sm.longitude).append("\" name=\"").append(sm.longName).
            append("\" netid=\"").append(sm.seedname.substring(0, 2)).append("\">\n");
    for (SM_INFO sm1 : sms) {
      sb.append(sm1.getShakeMapComponentXML());
    }
    sb.append("    </station>\n");
    sb.append("  </record>\n");
    sb.append("</amplitudes>\n");
    return sb.toString();
  }
}
