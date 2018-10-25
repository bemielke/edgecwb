/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package subspacedetector;

import detection.usgs.LabeledDetectionStatistic;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import gov.usgs.anss.util.SimpleSMTPThread;
import java.util.Date;

/**
 *
 * @author benz
 */
public final class Outputter {

  private PrintStream pf;
  private final SimpleDateFormat form = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

  public void prt(String s) {
    Util.prt(s);
  }

  public void prta(String s) {
    Util.prta(s);
  }

  public PrintStream getPrintStream() {
    return pf;
  }
  
  public Outputter() throws FileNotFoundException, IOException, InterruptedException {
  }

  public void openFile(String filename) throws FileNotFoundException, IOException, InterruptedException {
    pf = new PrintStream(filename);
  }

  public void openFileAppend(String filename) throws FileNotFoundException, IOException, InterruptedException {
    pf = new PrintStream(new FileOutputStream(filename, true));
  }

  public void closeFile(String filename) throws FileNotFoundException, IOException, InterruptedException {
    if (pf != null) {
      pf.close();
    }
  }

  public void writeStringtoFile(String t) {
    pf.print(t + "\n");
  }

  //
  // Debug Code used to write out the cross-correalation results
  //
  public void writeDetectionTime(int pick, GregorianCalendar ct, int offset,
          ArrayList< LabeledDetectionStatistic> ds, int rate, int avglen) {

    float tdur = (float) ((pick - offset)) / (float) (rate);
    GregorianCalendar peaktime = new GregorianCalendar();
    peaktime.setTimeInMillis(ct.getTimeInMillis());
    peaktime.add(Calendar.MILLISECOND, (int) (tdur * 1000));

    int year = ct.get(GregorianCalendar.YEAR);
    int month = ct.get(GregorianCalendar.MONTH) + 1;
    int dayofmonth = ct.get(GregorianCalendar.DAY_OF_MONTH);
    GregorianCalendar bday = new GregorianCalendar();
    String bb = year + "/" + month + "/" + dayofmonth + " 00:00";
    bday.setTimeInMillis(Util.stringToDate2(bb).getTime());
    float time = (float) ((float) (peaktime.getTimeInMillis() - bday.getTimeInMillis()) / 1000.0);

    pf.print(time + " " + ds.get(0).getDetectionStatistic()[pick - avglen] + "\n");
  }

  public void writeXYData(float[] x, float[] y) {
    for (int i = 0; i < x.length; i++) {
      pf.print(x[i] + " " + y[i] + "\n");
    }
  }

  public void writeDetectionStatistics(float[] data, int offset, GregorianCalendar t, float r) {
    int year = t.get(GregorianCalendar.YEAR);
    int month = t.get(GregorianCalendar.MONTH) + 1;
    int dayofmonth = t.get(GregorianCalendar.DAY_OF_MONTH);
    GregorianCalendar bday = new GregorianCalendar();
    String bb = year + "/" + month + "/" + dayofmonth + " 00:00";
    bday.setTimeInMillis(Util.stringToDate2(bb).getTime());
    float firstpointintime = (float) ((float) (t.getTimeInMillis() - bday.getTimeInMillis()) / 1000.0);

    float dt = (float) (1.0 / r);
    int npts = data.length - offset;

    for (int i = 0; i < npts; i++) {
      pf.printf("%13.4f %1.8f\n", firstpointintime + i * dt, data[i + offset]);
    }
  }

  public String getWeblink(DetectionSummary d, Config t, String nt, String st, String cp, String ll) {

    if (ll.equals("..")) {
      ll = "--";
    }
    String base = "http://service.iris.edu/irisws/timeseries/1/query?net=" + nt + "&sta=" + st + "&loc=" + ll + "&cha=" + cp;
    String start = form.format(new Date(d.PhaseTime().getTimeInMillis() - (5 * 1000)));
    String duration = Integer.toString((int) ((1. / t.getStatInfo().getRate()) * t.getTemplateLength()) + 10);
    base = base + "&starttime=" + start + "&dur=" + duration + "&demean&bp=1.4-4.4&output=plot";
    return base;
  }

  //
  // Debug code for writing out the summary of the maximum detection threshold
  // based on the empirical estimate of noise
  //
  public void writeSummary(DetectionSummary d, Config t,
          String nt, String st, String cp, String ll, float cc, float dthres, float SNR, float mag) {
    String weblink = getWeblink(d, t, nt, st, cp, ll);

    String contents = Util.ascdatetime2(d.OriginTime()) + " " + t.getReferenceLatitude() + " "
            + t.getReferenceLongitude() + " " + t.getReferenceDepth() + " " + String.format("%.2f", mag) + " "
            + t.getReferenceMagType() + " " + nt + " " + st + " " + cp + " " + ll + " " + t.getStatInfo().getPhaseLabel() + " "
            + Util.ascdatetime2(d.PhaseTime()) + " " + String.format("%.2f", cc) + " " + String.format("%.2f", dthres) + " " + String.format("%.2f", SNR) + " " + weblink + "\n";

    pf.print(contents);
    if (t.isEmail() && t.getEmailAddresses() != null) {
      Util.setProperty("SMTPFrom", "wyeck@usgs.gov");
      SimpleSMTPThread.setDebug(true);
      String subject = "New Correlation Detection at " + st + ": " + Util.ascdatetime2(d.OriginTime()) + " " + t.getReferenceLatitude() + " " + t.getReferenceLongitude();
      String line = "Estimated Origin Time: " + Util.ascdatetime2(d.OriginTime()) + "\nLatitude: " + t.getReferenceLatitude() + "\nLongitude: "
              + t.getReferenceLongitude() + "\nEstimated Magnitude: " + String.format("%.2f", mag) + "\nStation Info: " + nt + " " + st + " " + cp + " " + ll + "\nCorrelation Coefficient: " + String.format("%.2f", cc) + "\nSNR: " + String.format("%.2f", SNR) + "\nWaveform: " + weblink + "\n";

      String[] emails = t.getEmailAddresses();
      for (int i = 0; i < emails.length; i++) {
        SimpleSMTPThread message = SimpleSMTPThread.email(emails[i], subject, line);
        message.waitForDone();

      }

    }
  }

}
